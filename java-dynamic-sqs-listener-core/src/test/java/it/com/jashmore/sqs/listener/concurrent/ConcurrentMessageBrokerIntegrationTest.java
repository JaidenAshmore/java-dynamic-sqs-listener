package it.com.jashmore.sqs.listener.concurrent;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

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
import it.com.jashmore.sqs.AbstractSqsIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ConcurrentMessageBrokerIntegrationTest extends AbstractSqsIntegrationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final PayloadMapper PAYLOAD_MAPPER = new JacksonPayloadMapper(OBJECT_MAPPER);

    @Rule
    public LocalSqsRule localSqsRule = new LocalSqsRule();

    private String queueUrl;
    private SqsAsyncClient sqsAsyncClient;
    private QueueProperties queueProperties;
    private ArgumentResolverService argumentResolverService;

    @Before
    public void setUp() {
        queueUrl = localSqsRule.createRandomQueue();
        queueProperties = QueueProperties.builder().queueUrl(queueUrl).build();
        sqsAsyncClient = localSqsRule.getLocalAmazonSqsAsync();
        argumentResolverService = new CoreArgumentResolverService(PAYLOAD_MAPPER, sqsAsyncClient);
    }

    @After
    public void tearDown() {
        // If the thread running the tests is interrupted it will break future tests. This will be fixed in release of JUnit 4.13 but until then
        // we use this workaround. See https://github.com/junit-team/junit4/issues/1365
        Thread.interrupted();
    }

    @Test
    public void concurrentListenerCanConsumeMultipleMessagesFromQueueAtOnce() throws Exception {
        // arrange
        final int concurrencyLevel = 5;
        final MessageRetriever messageRetriever = new IndividualMessageRetriever(
                sqsAsyncClient,
                queueProperties,
                IndividualMessageRetrieverProperties.builder()
                        .visibilityTimeoutForMessagesInSeconds(5)
                        .build()
        );
        final CountDownLatch concurrentMessagesLatch = new CountDownLatch(concurrencyLevel);
        final MessageConsumer messageConsumer = new MessageConsumer(concurrentMessagesLatch);
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
        sendNumberOfMessages(concurrencyLevel, sqsAsyncClient, queueUrl);

        // act
        messageBroker.start();
        concurrentMessagesLatch.await(60, SECONDS);

        // cleanup
        messageBroker.stop().get(4, SECONDS);

        // assert
        assertThat(messageConsumer.numberOfTimesProcessed.get()).isEqualTo(concurrencyLevel);
        assertNoMessagesInQueue(sqsAsyncClient, queueUrl);
    }

    @Test
    public void allMessagesSentIntoQueueAreProcessed() throws Exception {
        // arrange
        final int concurrencyLevel = 10;
        final int numberOfMessages = 300;
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl(queueUrl).build();
        final SqsAsyncClient sqsAsyncClient = localSqsRule.getLocalAmazonSqsAsync();
        final MessageRetriever messageRetriever = new IndividualMessageRetriever(
                sqsAsyncClient,
                queueProperties,
                IndividualMessageRetrieverProperties.builder().visibilityTimeoutForMessagesInSeconds(1).build()
        );
        final CountDownLatch messageReceivedLatch = new CountDownLatch(numberOfMessages);
        final PayloadMapper payloadMapper = new JacksonPayloadMapper(OBJECT_MAPPER);
        final ArgumentResolverService argumentResolverService = new CoreArgumentResolverService(payloadMapper, sqsAsyncClient);
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
        sendNumberOfMessages(numberOfMessages, sqsAsyncClient, queueUrl);

        // act
        messageBroker.start();

        // assert
        messageReceivedLatch.await(60, SECONDS);
        assertThat(messageConsumer.numberOfTimesProcessed.get()).isEqualTo(numberOfMessages);

        // cleanup
        messageBroker.stop().get(4, SECONDS);
        assertNoMessagesInQueue(sqsAsyncClient, queueUrl);
    }

    @Test
    public void usingPrefetchingMessageRetrieverCanConsumeAllMessages() throws Exception {
        // arrange
        final int concurrencyLevel = 10;
        final int numberOfMessages = 300;
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl(queueUrl).build();
        final SqsAsyncClient sqsAsyncClient = localSqsRule.getLocalAmazonSqsAsync();
        final AsyncMessageRetriever messageRetriever = new PrefetchingMessageRetriever(
                sqsAsyncClient,
                queueProperties,
                StaticPrefetchingMessageRetrieverProperties
                        .builder()
                        .visibilityTimeoutForMessagesInSeconds(1)
                        .maxWaitTimeInSecondsToObtainMessagesFromServer(1)
                        .desiredMinPrefetchedMessages(30)
                        .maxPrefetchedMessages(40)
                        .build(),
                Executors.newCachedThreadPool()
        );
        final CountDownLatch messageReceivedLatch = new CountDownLatch(numberOfMessages);
        final PayloadMapper payloadMapper = new JacksonPayloadMapper(OBJECT_MAPPER);
        final ArgumentResolverService argumentResolverService = new CoreArgumentResolverService(payloadMapper, sqsAsyncClient);
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
        sendNumberOfMessages(numberOfMessages, sqsAsyncClient, queueUrl);
        messageRetriever.start();

        // act
        messageBroker.start();

        // assert
        messageReceivedLatch.await(1, MINUTES);
        assertThat(messageConsumer.numberOfTimesProcessed.get()).isEqualTo(numberOfMessages);

        // cleanup
        messageRetriever.stop().get(5, SECONDS);
        log.debug("Stopped message retriever");
        messageBroker.stop().get(10, SECONDS);
        assertNoMessagesInQueue(sqsAsyncClient, queueUrl);
    }

    @SuppressWarnings("WeakerAccess")
    public static class MessageConsumer {
        private final CountDownLatch messagesReceivedLatch;
        private final AtomicInteger numberOfTimesProcessed = new AtomicInteger(0);

        public MessageConsumer(final CountDownLatch messagesReceivedLatch) {
            this.messagesReceivedLatch = messagesReceivedLatch;
        }

        @SuppressWarnings("unused")
        public void consume(@Payload final String messagePayload) {
            log.info("Consuming message: {}", messagePayload);
            numberOfTimesProcessed.incrementAndGet();
            messagesReceivedLatch.countDown();
        }
    }
}
