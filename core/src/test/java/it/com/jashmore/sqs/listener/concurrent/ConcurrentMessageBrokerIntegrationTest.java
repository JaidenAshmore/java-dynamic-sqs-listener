package it.com.jashmore.sqs.listener.concurrent;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.CoreArgumentResolverService;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.CoreMessageListenerContainer;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.processor.CoreMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever;
import com.jashmore.sqs.retriever.batching.StaticBatchingMessageRetrieverProperties;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.StaticPrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.util.CreateRandomQueueResponse;
import it.com.jashmore.sqs.util.SqsIntegrationTestUtils;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class ConcurrentMessageBrokerIntegrationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final PayloadMapper PAYLOAD_MAPPER = new JacksonPayloadMapper(OBJECT_MAPPER);

    private static final ElasticMqSqsAsyncClient elasticMQSqsAsyncClient = new ElasticMqSqsAsyncClient();

    private QueueProperties queueProperties;
    private ArgumentResolverService argumentResolverService;

    @BeforeEach
    void setUp() throws InterruptedException, ExecutionException, TimeoutException {
        queueProperties =
            elasticMQSqsAsyncClient
                .createRandomQueue()
                .thenApply(CreateRandomQueueResponse::queueUrl)
                .thenApply(url -> QueueProperties.builder().queueUrl(url).build())
                .get(5, SECONDS);
        argumentResolverService = new CoreArgumentResolverService(PAYLOAD_MAPPER, OBJECT_MAPPER);
    }

    @Test
    void allMessagesSentIntoQueueAreProcessed() throws Exception {
        // arrange
        final int concurrencyLevel = 10;
        final int numberOfMessages = 100;
        final MessageRetriever messageRetriever = new BatchingMessageRetriever(
            queueProperties,
            elasticMQSqsAsyncClient,
            StaticBatchingMessageRetrieverProperties.builder().batchSize(1).build()
        );
        final CountDownLatch messageReceivedLatch = new CountDownLatch(numberOfMessages);
        final MessageConsumer messageConsumer = new MessageConsumer(messageReceivedLatch);
        final MessageResolver messageResolver = new BatchingMessageResolver(queueProperties, elasticMQSqsAsyncClient);
        final MessageProcessor messageProcessor = new CoreMessageProcessor(
            argumentResolverService,
            queueProperties,
            elasticMQSqsAsyncClient,
            MessageConsumer.class.getMethod("consume", String.class),
            messageConsumer
        );
        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
            StaticConcurrentMessageBrokerProperties.builder().concurrencyLevel(concurrencyLevel).build()
        );
        SqsIntegrationTestUtils.sendNumberOfMessages(numberOfMessages, elasticMQSqsAsyncClient, queueProperties.getQueueUrl());
        final CoreMessageListenerContainer coreMessageListenerContainer = new CoreMessageListenerContainer(
            "id",
            () -> messageBroker,
            () -> messageRetriever,
            () -> messageProcessor,
            () -> messageResolver
        );

        // act
        coreMessageListenerContainer.start();

        // assert
        messageReceivedLatch.await(60, SECONDS);

        // cleanup
        coreMessageListenerContainer.stop();
        SqsIntegrationTestUtils.assertNoMessagesInQueue(elasticMQSqsAsyncClient, queueProperties.getQueueUrl());
    }

    @Test
    void usingPrefetchingMessageRetrieverCanConsumeAllMessages() throws Exception {
        // arrange
        final int concurrencyLevel = 10;
        final int numberOfMessages = 100;
        final MessageRetriever messageRetriever = new PrefetchingMessageRetriever(
            elasticMQSqsAsyncClient,
            queueProperties,
            StaticPrefetchingMessageRetrieverProperties
                .builder()
                .messageVisibilityTimeout(Duration.ofSeconds(60))
                .desiredMinPrefetchedMessages(30)
                .maxPrefetchedMessages(40)
                .build()
        );
        final CountDownLatch messageReceivedLatch = new CountDownLatch(numberOfMessages);
        final MessageConsumer messageConsumer = new MessageConsumer(messageReceivedLatch);
        final MessageResolver messageResolver = new BatchingMessageResolver(queueProperties, elasticMQSqsAsyncClient);
        final MessageProcessor messageProcessor = new CoreMessageProcessor(
            argumentResolverService,
            queueProperties,
            elasticMQSqsAsyncClient,
            MessageConsumer.class.getMethod("consume", String.class),
            messageConsumer
        );
        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
            StaticConcurrentMessageBrokerProperties.builder().concurrencyLevel(concurrencyLevel).build()
        );
        final CoreMessageListenerContainer coreMessageListenerContainer = new CoreMessageListenerContainer(
            "id",
            () -> messageBroker,
            () -> messageRetriever,
            () -> messageProcessor,
            () -> messageResolver
        );

        SqsIntegrationTestUtils.sendNumberOfMessages(numberOfMessages, elasticMQSqsAsyncClient, queueProperties.getQueueUrl());

        // act
        coreMessageListenerContainer.start();

        // assert
        messageReceivedLatch.await(1, MINUTES);

        // cleanup
        coreMessageListenerContainer.stop();
        SqsIntegrationTestUtils.assertNoMessagesInQueue(elasticMQSqsAsyncClient, queueProperties.getQueueUrl());
    }

    @Test
    void usingBatchingMessageRetrieverCanConsumeAllMessages() throws Exception {
        // arrange
        final int concurrencyLevel = 10;
        final int numberOfMessages = 100;
        final MessageRetriever messageRetriever = new BatchingMessageRetriever(
            queueProperties,
            elasticMQSqsAsyncClient,
            StaticBatchingMessageRetrieverProperties
                .builder()
                .batchSize(10)
                .batchingPeriod(Duration.ofSeconds(3))
                .messageVisibilityTimeout(Duration.ofSeconds(60))
                .build()
        );
        final CountDownLatch messageReceivedLatch = new CountDownLatch(numberOfMessages);
        final MessageConsumer messageConsumer = new MessageConsumer(messageReceivedLatch);
        final MessageResolver messageResolver = new BatchingMessageResolver(queueProperties, elasticMQSqsAsyncClient);
        final MessageProcessor messageProcessor = new CoreMessageProcessor(
            argumentResolverService,
            queueProperties,
            elasticMQSqsAsyncClient,
            MessageConsumer.class.getMethod("consume", String.class),
            messageConsumer
        );
        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
            StaticConcurrentMessageBrokerProperties.builder().concurrencyLevel(concurrencyLevel).build()
        );
        final CoreMessageListenerContainer coreMessageListenerContainer = new CoreMessageListenerContainer(
            "id",
            () -> messageBroker,
            () -> messageRetriever,
            () -> messageProcessor,
            () -> messageResolver
        );

        SqsIntegrationTestUtils.sendNumberOfMessages(numberOfMessages, elasticMQSqsAsyncClient, queueProperties.getQueueUrl());

        // act
        coreMessageListenerContainer.start();

        // assert
        messageReceivedLatch.await(1, MINUTES);

        // cleanup
        coreMessageListenerContainer.stop();
        SqsIntegrationTestUtils.assertNoMessagesInQueue(elasticMQSqsAsyncClient, queueProperties.getQueueUrl());
    }

    @SuppressWarnings("WeakerAccess")
    public static class MessageConsumer {
        private final CountDownLatch messagesReceivedLatch;

        public MessageConsumer(final CountDownLatch messagesReceivedLatch) {
            this.messagesReceivedLatch = messagesReceivedLatch;
        }

        @SuppressWarnings("unused")
        public void consume(@Payload final String messagePayload) {
            log.info("Consuming message: {}", messagePayload);
            messagesReceivedLatch.countDown();
        }
    }
}
