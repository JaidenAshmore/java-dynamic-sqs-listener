package it.com.jashmore.sqs.argument;

import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.CoreArgumentResolverService;
import com.jashmore.sqs.argument.attribute.MessageSystemAttribute;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.CoreMessageListenerContainer;
import com.jashmore.sqs.processor.CoreMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever;
import com.jashmore.sqs.retriever.batching.StaticBatchingMessageRetrieverProperties;
import com.jashmore.sqs.test.LocalSqsRule;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class MessageSystemAttributeIntegrationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final PayloadMapper PAYLOAD_MAPPER = new JacksonPayloadMapper(OBJECT_MAPPER);
    private static final ArgumentResolverService ARGUMENT_RESOLVER_SERVICE = new CoreArgumentResolverService(PAYLOAD_MAPPER, OBJECT_MAPPER);

    @Rule
    public LocalSqsRule localSqsRule = new LocalSqsRule();

    private String queueUrl;
    private QueueProperties queueProperties;

    @Before
    public void setUp() {
        queueUrl = localSqsRule.createRandomQueue();
        queueProperties = QueueProperties.builder().queueUrl(queueUrl).build();
    }

    @Test
    public void messageAttributesCanBeConsumedInMessageProcessingMethods() throws Exception {
        // arrange
        final SqsAsyncClient sqsAsyncClient = localSqsRule.getLocalAmazonSqsAsync();
        final MessageRetriever messageRetriever = new BatchingMessageRetriever(
                queueProperties,
                sqsAsyncClient,
                StaticBatchingMessageRetrieverProperties.builder().batchSize(1).build()
        );
        final CountDownLatch messageProcessedLatch = new CountDownLatch(1);
        final AtomicReference<OffsetDateTime> messageAttributeReference = new AtomicReference<>();
        final MessageConsumer messageConsumer = new MessageConsumer(messageProcessedLatch, messageAttributeReference);
        final MessageResolver messageResolver = new BatchingMessageResolver(queueProperties, sqsAsyncClient);
        final MessageProcessor messageProcessor = new CoreMessageProcessor(
                ARGUMENT_RESOLVER_SERVICE,
                queueProperties,
                sqsAsyncClient,
                MessageConsumer.class.getMethod("consume", OffsetDateTime.class),
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

        assertThat(messageProcessedLatch.await(5, SECONDS)).isTrue();
        coreMessageListenerContainer.stop();

        // assert
        assertThat(messageAttributeReference.get()).isCloseTo(OffsetDateTime.now(), within(2, MINUTES));
    }

    @SuppressWarnings("WeakerAccess")
    @AllArgsConstructor
    public static class MessageConsumer {
        private final CountDownLatch latch;
        private final AtomicReference<OffsetDateTime> valueAtomicReference;

        public void consume(@MessageSystemAttribute(MessageSystemAttributeName.APPROXIMATE_FIRST_RECEIVE_TIMESTAMP) final OffsetDateTime value) {
            log.info("Message processed with attribute: {}", value);
            valueAtomicReference.set(value);
            latch.countDown();
        }
    }
}
