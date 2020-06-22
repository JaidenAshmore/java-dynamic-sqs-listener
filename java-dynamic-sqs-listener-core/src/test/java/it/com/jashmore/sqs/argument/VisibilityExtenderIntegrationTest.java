package it.com.jashmore.sqs.argument;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.CoreArgumentResolverService;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.CoreMessageListenerContainer;
import com.jashmore.sqs.processor.CoreMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.processor.argument.VisibilityExtender;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever;
import com.jashmore.sqs.retriever.batching.StaticBatchingMessageRetrieverProperties;
import com.jashmore.sqs.test.LocalSqsExtension;
import com.jashmore.sqs.util.SqsQueuesConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
class VisibilityExtenderIntegrationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final PayloadMapper PAYLOAD_MAPPER = new JacksonPayloadMapper(OBJECT_MAPPER);
    private static final ArgumentResolverService ARGUMENT_RESOLVER_SERVICE = new CoreArgumentResolverService(PAYLOAD_MAPPER, OBJECT_MAPPER);
    private static final int ORIGINAL_MESSAGE_VISIBILITY = 2;

    @RegisterExtension
    static LocalSqsExtension LOCAL_SQS = new LocalSqsExtension(ImmutableList.of(
            SqsQueuesConfig.QueueConfig.builder()
                    .queueName("VisibilityExtenderIntegrationTest")
                    .visibilityTimeout(ORIGINAL_MESSAGE_VISIBILITY)
                    .maxReceiveCount(2) // make sure it will try multiple times
                    .build()
    ));

    private String queueUrl;
    private QueueProperties queueProperties;

    @BeforeEach
    void setUp() {
        queueUrl = LOCAL_SQS.createRandomQueue();
        queueProperties = QueueProperties.builder().queueUrl(queueUrl).build();
    }

    @Test
    void messageAttributesCanBeConsumedInMessageProcessingMethods() throws Exception {
        // arrange
        final SqsAsyncClient sqsAsyncClient = LOCAL_SQS.getLocalAmazonSqsAsync();
        final MessageRetriever messageRetriever = new BatchingMessageRetriever(
                queueProperties,
                sqsAsyncClient,
                StaticBatchingMessageRetrieverProperties.builder().batchSize(1).build()
        );
        final CountDownLatch messageProcessedLatch = new CountDownLatch(1);
        final AtomicInteger numberTimesMessageProcessed = new AtomicInteger(0);
        final MessageConsumer messageConsumer = new MessageConsumer(messageProcessedLatch, numberTimesMessageProcessed);
        final MessageResolver messageResolver = new BatchingMessageResolver(queueProperties, sqsAsyncClient);
        final MessageProcessor messageProcessor = new CoreMessageProcessor(
                ARGUMENT_RESOLVER_SERVICE,
                queueProperties,
                sqsAsyncClient,
                MessageConsumer.class.getMethod("consume", VisibilityExtender.class),
                messageConsumer
        );
        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
                StaticConcurrentMessageBrokerProperties.builder()
                        .concurrencyLevel(1)
                        .build()
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
        sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody("test")
                .build())
                .get(2, SECONDS);

        assertThat(messageProcessedLatch.await(ORIGINAL_MESSAGE_VISIBILITY * 3, SECONDS)).isTrue();
        coreMessageListenerContainer.stop();

        // assert
        assertThat(numberTimesMessageProcessed).hasValue(1);
    }

    @AllArgsConstructor
    public static class MessageConsumer {
        private final CountDownLatch latch;
        private final AtomicInteger numberTimesEntered;

        @SuppressWarnings("WeakerAccess")
        public void consume(VisibilityExtender visibilityExtender) throws Exception {
            numberTimesEntered.incrementAndGet();
            // Extend it past what the current available visibility is
            visibilityExtender.extend(3 * ORIGINAL_MESSAGE_VISIBILITY).get();
            Thread.sleep(2 * ORIGINAL_MESSAGE_VISIBILITY * 1000);
            latch.countDown();
        }
    }
}
