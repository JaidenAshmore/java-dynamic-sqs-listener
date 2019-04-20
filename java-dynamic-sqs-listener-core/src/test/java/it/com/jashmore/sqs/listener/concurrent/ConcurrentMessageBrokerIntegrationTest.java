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
import com.jashmore.sqs.broker.concurrent.properties.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.processor.resolver.MessageResolver;
import com.jashmore.sqs.processor.resolver.individual.IndividualMessageResolver;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.MessageRetriever;
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
import java.util.concurrent.Executors;

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
        argumentResolverService = new CoreArgumentResolverService(PAYLOAD_MAPPER, localSqsRule.getLocalAmazonSqsAsync());
    }

    @Test
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
        final MessageResolver messageResolver = new IndividualMessageResolver(queueProperties, sqsAsyncClient);
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                argumentResolverService,
                queueProperties,
                messageResolver,
                MessageConsumer.class.getMethod("consume", String.class),
                messageConsumer
        );
        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
                messageRetriever,
                messageProcessor,
                Executors.newCachedThreadPool(),
                StaticConcurrentMessageBrokerProperties.builder()
                        .concurrencyLevel(concurrencyLevel)
                        .build()
        );
        SqsIntegrationTestUtils.sendNumberOfMessages(numberOfMessages, sqsAsyncClient, queueUrl);

        // act
        messageBroker.start();

        // assert
        messageReceivedLatch.await(60, SECONDS);

        // cleanup
        messageBroker.stop().get(4, SECONDS);
        SqsIntegrationTestUtils.assertNoMessagesInQueue(sqsAsyncClient, queueUrl);
    }

    @Test
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
                        .build(),
                Executors.newCachedThreadPool()
        );
        final CountDownLatch messageReceivedLatch = new CountDownLatch(numberOfMessages);
        final MessageConsumer messageConsumer = new MessageConsumer(messageReceivedLatch);
        final MessageResolver messageResolver = new IndividualMessageResolver(queueProperties, sqsAsyncClient);
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                argumentResolverService,
                queueProperties,
                messageResolver,
                MessageConsumer.class.getMethod("consume", String.class),
                messageConsumer
        );
        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
                messageRetriever,
                messageProcessor,
                Executors.newCachedThreadPool(),
                StaticConcurrentMessageBrokerProperties.builder()
                        .concurrencyLevel(concurrencyLevel)
                        .build()
        );
        SqsIntegrationTestUtils.sendNumberOfMessages(numberOfMessages, sqsAsyncClient, queueUrl);
        messageRetriever.start();

        // act
        messageBroker.start();

        // assert
        messageReceivedLatch.await(1, MINUTES);

        // cleanup
        messageRetriever.stop().get(5, SECONDS);
        log.debug("Stopped message retriever");
        messageBroker.stop().get(10, SECONDS);
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
