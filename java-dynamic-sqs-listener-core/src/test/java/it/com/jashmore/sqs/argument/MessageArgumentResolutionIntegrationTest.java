package it.com.jashmore.sqs.argument;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.CoreArgumentResolverService;
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
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.individual.IndividualMessageRetriever;
import com.jashmore.sqs.retriever.individual.IndividualMessageRetrieverProperties;
import com.jashmore.sqs.test.LocalSqsRule;
import lombok.AllArgsConstructor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class MessageArgumentResolutionIntegrationTest {
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
        argumentResolverService = new CoreArgumentResolverService(PAYLOAD_MAPPER, localSqsRule.getLocalAmazonSqsAsync(), OBJECT_MAPPER);
    }

    @Test
    public void messageAttributesCanBeConsumedInMessageProcessingMethods() throws Exception {
        // arrange
        final SqsAsyncClient sqsAsyncClient = localSqsRule.getLocalAmazonSqsAsync();
        final MessageRetriever messageRetriever = new IndividualMessageRetriever(
                sqsAsyncClient,
                queueProperties,
                IndividualMessageRetrieverProperties.builder()
                        .visibilityTimeoutForMessagesInSeconds(30)
                        .build()
        );
        final CountDownLatch messageProcessedLatch = new CountDownLatch(1);
        final AtomicReference<Message> messageAttributeReference = new AtomicReference<>();
        final MessageConsumer messageConsumer = new MessageConsumer(messageProcessedLatch, messageAttributeReference);
        final MessageResolver messageResolver = new BlockingMessageResolver(new IndividualMessageResolver(queueProperties, sqsAsyncClient));
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                argumentResolverService,
                queueProperties,
                messageResolver,
                MessageConsumer.class.getMethod("consume", Message.class),
                messageConsumer
        );
        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
                messageRetriever,
                messageProcessor,
                StaticConcurrentMessageBrokerProperties.builder()
                        .concurrencyLevel(1)
                        .build()
        );
        final SimpleMessageListenerContainer simpleMessageListenerContainer = new SimpleMessageListenerContainer(
                messageRetriever, messageBroker, messageResolver
        );
        simpleMessageListenerContainer.start();

        // act
        sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody("test")
                .build())
                .get(2, SECONDS);

        assertThat(messageProcessedLatch.await(5, SECONDS)).isTrue();
        simpleMessageListenerContainer.stop();

        // assert
        assertThat(messageAttributeReference.get().body()).isEqualTo("test");
    }

    @SuppressWarnings("WeakerAccess")
    @AllArgsConstructor
    public static class MessageConsumer {
        private final CountDownLatch latch;
        private final AtomicReference<Message> valueAtomicReference;

        public void consume(final Message message) {
            latch.countDown();
            valueAtomicReference.set(message);
        }
    }
}
