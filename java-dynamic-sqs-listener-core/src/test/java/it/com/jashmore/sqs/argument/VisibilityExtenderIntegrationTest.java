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
import com.jashmore.sqs.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.processor.argument.VisibilityExtender;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.blocking.BlockingMessageResolver;
import com.jashmore.sqs.resolver.individual.IndividualMessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.individual.IndividualMessageRetriever;
import com.jashmore.sqs.retriever.individual.StaticIndividualMessageRetrieverProperties;
import com.jashmore.sqs.test.LocalSqsRule;
import com.jashmore.sqs.util.SqsQueuesConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class VisibilityExtenderIntegrationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final PayloadMapper PAYLOAD_MAPPER = new JacksonPayloadMapper(OBJECT_MAPPER);
    private static final ArgumentResolverService ARGUMENT_RESOLVER_SERVICE = new CoreArgumentResolverService(PAYLOAD_MAPPER, OBJECT_MAPPER);
    private static final int ORIGINAL_MESSAGE_VISIBILITY = 5;

    @Rule
    public LocalSqsRule localSqsRule = new LocalSqsRule(ImmutableList.of(
            SqsQueuesConfig.QueueConfig.builder()
                    .queueName("VisibilityExtenderIntegrationTest")
                    .visibilityTimeout(ORIGINAL_MESSAGE_VISIBILITY)
                    .maxReceiveCount(2) // make sure it will try multiple times
                    .build()
    ));

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
        final MessageRetriever messageRetriever = new IndividualMessageRetriever(
                sqsAsyncClient,
                queueProperties,
                StaticIndividualMessageRetrieverProperties.builder()
                        .visibilityTimeoutForMessagesInSeconds(ORIGINAL_MESSAGE_VISIBILITY)
                        .build()
        );
        final CountDownLatch messageProcessedLatch = new CountDownLatch(1);
        final AtomicInteger numberTimesMessageProcesed = new AtomicInteger(0);
        final MessageConsumer messageConsumer = new MessageConsumer(messageProcessedLatch, numberTimesMessageProcesed);
        final MessageResolver messageResolver = new BlockingMessageResolver(new IndividualMessageResolver(queueProperties, sqsAsyncClient));
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                ARGUMENT_RESOLVER_SERVICE,
                queueProperties,
                sqsAsyncClient,
                messageResolver,
                MessageConsumer.class.getMethod("consume", VisibilityExtender.class),
                messageConsumer
        );
        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
                messageRetriever,
                messageProcessor,
                StaticConcurrentMessageBrokerProperties.builder()
                        .concurrencyLevel(2)
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

        assertThat(messageProcessedLatch.await(ORIGINAL_MESSAGE_VISIBILITY * 3, SECONDS)).isTrue();
        simpleMessageListenerContainer.stop();

        // assert
        assertThat(numberTimesMessageProcesed).hasValue(1);
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
