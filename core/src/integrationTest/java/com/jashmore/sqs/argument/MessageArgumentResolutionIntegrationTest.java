package com.jashmore.sqs.argument;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
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
import com.jashmore.sqs.util.CreateRandomQueueResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AllArgsConstructor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

class MessageArgumentResolutionIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final PayloadMapper PAYLOAD_MAPPER = new JacksonPayloadMapper(OBJECT_MAPPER);
    private static final ArgumentResolverService ARGUMENT_RESOLVER_SERVICE = new CoreArgumentResolverService(PAYLOAD_MAPPER, OBJECT_MAPPER);

    private static final ElasticMqSqsAsyncClient elasticMQSqsAsyncClient = new ElasticMqSqsAsyncClient();

    private QueueProperties queueProperties;

    @BeforeEach
    void setUp() throws InterruptedException, ExecutionException, TimeoutException {
        queueProperties =
            elasticMQSqsAsyncClient
                .createRandomQueue()
                .thenApply(CreateRandomQueueResponse::queueUrl)
                .thenApply(url -> QueueProperties.builder().queueUrl(url).build())
                .get(5, SECONDS);
    }

    @AfterAll
    static void tearDown() {
        elasticMQSqsAsyncClient.close();
    }

    @Test
    void messageAttributesCanBeConsumedInMessageProcessingMethods() throws Exception {
        // arrange
        final MessageRetriever messageRetriever = new BatchingMessageRetriever(
            queueProperties,
            elasticMQSqsAsyncClient,
            StaticBatchingMessageRetrieverProperties.builder().batchSize(1).build()
        );
        final CountDownLatch messageProcessedLatch = new CountDownLatch(1);
        final AtomicReference<Message> messageAttributeReference = new AtomicReference<>();
        final MessageConsumer messageConsumer = new MessageConsumer(messageProcessedLatch, messageAttributeReference);
        final MessageResolver messageResolver = new BatchingMessageResolver(queueProperties, elasticMQSqsAsyncClient);
        final MessageProcessor messageProcessor = new CoreMessageProcessor(
            ARGUMENT_RESOLVER_SERVICE,
            queueProperties,
            elasticMQSqsAsyncClient,
            MessageConsumer.class.getMethod("consume", Message.class),
            messageConsumer
        );
        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
            StaticConcurrentMessageBrokerProperties.builder().concurrencyLevel(1).build()
        );
        final CoreMessageListenerContainer coreMessageListenerContainer = new CoreMessageListenerContainer(
            "id",
            () -> messageBroker,
            () -> messageRetriever,
            () -> messageProcessor,
            () -> messageResolver
        );
        coreMessageListenerContainer.start();

        // act
        elasticMQSqsAsyncClient
            .sendMessage(SendMessageRequest.builder().queueUrl(queueProperties.getQueueUrl()).messageBody("test").build())
            .get(2, SECONDS);

        assertThat(messageProcessedLatch.await(5, SECONDS)).isTrue();
        coreMessageListenerContainer.stop();

        // assert
        assertThat(messageAttributeReference.get().body()).isEqualTo("test");
    }

    @SuppressWarnings("WeakerAccess")
    @AllArgsConstructor
    public static class MessageConsumer {

        private final CountDownLatch latch;
        private final AtomicReference<Message> valueAtomicReference;

        public void consume(final Message message) {
            valueAtomicReference.set(message);
            latch.countDown();
        }
    }
}
