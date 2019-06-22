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
import com.jashmore.sqs.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.blocking.BlockingMessageResolver;
import com.jashmore.sqs.resolver.individual.IndividualMessageResolver;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever;
import com.jashmore.sqs.retriever.batching.StaticBatchingMessageRetrieverProperties;
import com.jashmore.sqs.retriever.individual.IndividualMessageRetriever;
import com.jashmore.sqs.retriever.individual.IndividualMessageRetrieverProperties;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.StaticPrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.test.LocalSqsRule;
import it.com.jashmore.sqs.listener.util.SqsIntegrationTestUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.concurrent.CountDownLatch;

@Slf4j
public class ConcurrentMessageBrokerIntegrationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final PayloadMapper PAYLOAD_MAPPER = new JacksonPayloadMapper(OBJECT_MAPPER);

    @Rule
    public LocalSqsRule localSqsRule = new LocalSqsRule();

    private String queueUrl;
    private QueueProperties queueProperties;
    private ArgumentResolverService argumentResolverService;

    @Before
    public void setUp() {
        queueUrl = localSqsRule.createRandomQueue();
        queueProperties = QueueProperties.builder().queueUrl(queueUrl).build();
        argumentResolverService = new CoreArgumentResolverService(PAYLOAD_MAPPER, OBJECT_MAPPER);
    }

    @Test(timeout = 30_000)
    public void allMessagesSentIntoQueueAreProcessed() throws Exception {
        // arrange
        final int concurrencyLevel = 10;
        final int numberOfMessages = 100;
        final SqsAsyncClient sqsAsyncClient = localSqsRule.getLocalAmazonSqsAsync();
        final MessageRetriever messageRetriever = new IndividualMessageRetriever(
                sqsAsyncClient,
                queueProperties,
                IndividualMessageRetrieverProperties.builder()
                        .visibilityTimeoutForMessagesInSeconds(30)
                        .build()
        );
        final CountDownLatch messageReceivedLatch = new CountDownLatch(numberOfMessages);
        final MessageConsumer messageConsumer = new MessageConsumer(messageReceivedLatch);
        final MessageResolver messageResolver = new BlockingMessageResolver(new IndividualMessageResolver(queueProperties, sqsAsyncClient));
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                argumentResolverService,
                queueProperties,
                sqsAsyncClient,
                messageResolver,
                MessageConsumer.class.getMethod("consume", String.class),
                messageConsumer
        );
        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
                messageRetriever,
                messageProcessor,
                StaticConcurrentMessageBrokerProperties.builder()
                        .concurrencyLevel(concurrencyLevel)
                        .build()
        );
        SqsIntegrationTestUtils.sendNumberOfMessages(numberOfMessages, sqsAsyncClient, queueUrl);
        final SimpleMessageListenerContainer simpleMessageListenerContainer = new SimpleMessageListenerContainer(
                messageRetriever, messageBroker, messageResolver
        );

        // act
        simpleMessageListenerContainer.start();

        // assert
        messageReceivedLatch.await(60, SECONDS);

        // cleanup
        simpleMessageListenerContainer.stop();
        SqsIntegrationTestUtils.assertNoMessagesInQueue(sqsAsyncClient, queueUrl);
    }

    @Test(timeout = 30_000)
    public void usingPrefetchingMessageRetrieverCanConsumeAllMessages() throws Exception {
        // arrange
        final int concurrencyLevel = 10;
        final int numberOfMessages = 100;
        final SqsAsyncClient sqsAsyncClient = localSqsRule.getLocalAmazonSqsAsync();
        final AsyncMessageRetriever messageRetriever = new PrefetchingMessageRetriever(
                sqsAsyncClient,
                queueProperties,
                StaticPrefetchingMessageRetrieverProperties
                        .builder()
                        .visibilityTimeoutForMessagesInSeconds(60)
                        .maxWaitTimeInSecondsToObtainMessagesFromServer(1)
                        .desiredMinPrefetchedMessages(30)
                        .maxPrefetchedMessages(40)
                        .build()
        );
        final CountDownLatch messageReceivedLatch = new CountDownLatch(numberOfMessages);
        final MessageConsumer messageConsumer = new MessageConsumer(messageReceivedLatch);
        final MessageResolver messageResolver = new BlockingMessageResolver(new IndividualMessageResolver(queueProperties, sqsAsyncClient));
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                argumentResolverService,
                queueProperties,
                sqsAsyncClient,
                messageResolver,
                MessageConsumer.class.getMethod("consume", String.class),
                messageConsumer
        );
        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
                messageRetriever,
                messageProcessor,
                StaticConcurrentMessageBrokerProperties.builder()
                        .concurrencyLevel(concurrencyLevel)
                        .build()
        );
        final SimpleMessageListenerContainer simpleMessageListenerContainer = new SimpleMessageListenerContainer(
                messageRetriever, messageBroker, messageResolver
        );

        SqsIntegrationTestUtils.sendNumberOfMessages(numberOfMessages, sqsAsyncClient, queueUrl);

        // act
        simpleMessageListenerContainer.start();

        // assert
        messageReceivedLatch.await(1, MINUTES);

        // cleanup
        simpleMessageListenerContainer.stop();
        SqsIntegrationTestUtils.assertNoMessagesInQueue(sqsAsyncClient, queueUrl);
    }


    @Test(timeout = 30_000)
    public void usingBatchingMessageRetrieverCanConsumeAllMessages() throws Exception {
        // arrange
        final int concurrencyLevel = 10;
        final int numberOfMessages = 100;
        final SqsAsyncClient sqsAsyncClient = localSqsRule.getLocalAmazonSqsAsync();
        final AsyncMessageRetriever messageRetriever = new BatchingMessageRetriever(
                queueProperties,
                sqsAsyncClient,
                StaticBatchingMessageRetrieverProperties.builder()
                        .numberOfThreadsWaitingTrigger(10)
                        .messageRetrievalPollingPeriodInMs(3000L)
                        .visibilityTimeoutInSeconds(60)
                .build()
        );
        final CountDownLatch messageReceivedLatch = new CountDownLatch(numberOfMessages);
        final MessageConsumer messageConsumer = new MessageConsumer(messageReceivedLatch);
        final MessageResolver messageResolver = new BlockingMessageResolver(new IndividualMessageResolver(queueProperties, sqsAsyncClient));
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                argumentResolverService,
                queueProperties,
                sqsAsyncClient,
                messageResolver,
                MessageConsumer.class.getMethod("consume", String.class),
                messageConsumer
        );
        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
                messageRetriever,
                messageProcessor,
                StaticConcurrentMessageBrokerProperties.builder()
                        .concurrencyLevel(concurrencyLevel)
                        .build()
        );
        final SimpleMessageListenerContainer simpleMessageListenerContainer = new SimpleMessageListenerContainer(
                messageRetriever, messageBroker, messageResolver
        );

        SqsIntegrationTestUtils.sendNumberOfMessages(numberOfMessages, sqsAsyncClient, queueUrl);

        // act
        simpleMessageListenerContainer.start();

        // assert
        messageReceivedLatch.await(1, MINUTES);

        // cleanup
        simpleMessageListenerContainer.stop();
        SqsIntegrationTestUtils.assertNoMessagesInQueue(sqsAsyncClient, queueUrl);
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
