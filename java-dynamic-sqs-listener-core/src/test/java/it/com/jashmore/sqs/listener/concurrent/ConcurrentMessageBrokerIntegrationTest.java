package it.com.jashmore.sqs.listener.concurrent;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DefaultArgumentResolverService;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingProperties;
import com.jashmore.sqs.retriever.individual.IndividualMessageRetriever;
import com.jashmore.sqs.retriever.individual.IndividualMessageRetrieverProperties;
import com.jashmore.sqs.test.LocalSqsRule;
import it.com.jashmore.sqs.AbstractSqsIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ConcurrentMessageBrokerIntegrationTest extends AbstractSqsIntegrationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final PayloadMapper PAYLOAD_MAPPER = new JacksonPayloadMapper(OBJECT_MAPPER);

    @Rule
    public LocalSqsRule localSqsRule = new LocalSqsRule();

    private String queueUrl;

    @Before
    public void setUp() {
        queueUrl = localSqsRule.createRandomQueue();
    }

    @Test
    public void concurrentListenerCanConsumeMultipleMessagesFromQueueAtOnce() throws Exception {
        // arrange
        final int concurrencyLevel = 5;
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl(queueUrl).build();
        final AmazonSQSAsync amazonSqsAsync = localSqsRule.getAmazonSqsAsync();
        final MessageRetriever messageRetriever = new IndividualMessageRetriever(
                amazonSqsAsync,
                queueProperties,
                IndividualMessageRetrieverProperties.builder().visibilityTimeoutForMessagesInSeconds(5).build()
        );
        final CountDownLatch messageReceivedLatch = new CountDownLatch(concurrencyLevel);
        final CountDownLatch testCompletedLatch = new CountDownLatch(1);
        final ArgumentResolverService argumentResolverService = new DefaultArgumentResolverService(PAYLOAD_MAPPER, amazonSqsAsync);
        final MessageConsumer messageConsumer = new MessageConsumer(messageReceivedLatch, testCompletedLatch);
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                argumentResolverService,
                queueProperties,
                amazonSqsAsync,
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
        sendNumberOfMessages(concurrencyLevel, localSqsRule.getAmazonSqsAsync(), queueUrl);

        // act
        messageBroker.start();

        // assert
        messageReceivedLatch.await(1, SECONDS);
        assertThat(messageConsumer.numberOfTimesProcessed.get()).isEqualTo(concurrencyLevel);

        // cleanup
        final Future<?> containerStoppedFuture = messageBroker.stop();
        testCompletedLatch.countDown();
        containerStoppedFuture.get(4, SECONDS);
        assertNoMessagesInQueue(localSqsRule.getAmazonSqsAsync(), queueUrl);
    }

    @Test
    public void allMessagesSentIntoQueueAreProcessed() throws Exception {
        // arrange
        final int concurrencyLevel = 10;
        final int numberOfMessages = 300;
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl(queueUrl).build();
        final AmazonSQSAsync amazonSqsAsync = localSqsRule.getAmazonSqsAsync();
        final MessageRetriever messageRetriever = new IndividualMessageRetriever(
                amazonSqsAsync,
                queueProperties,
                IndividualMessageRetrieverProperties.builder().visibilityTimeoutForMessagesInSeconds(1).build()
        );
        final CountDownLatch messageReceivedLatch = new CountDownLatch(numberOfMessages);
        final PayloadMapper payloadMapper = new JacksonPayloadMapper(OBJECT_MAPPER);
        final ArgumentResolverService argumentResolverService = new DefaultArgumentResolverService(payloadMapper, amazonSqsAsync);
        final MessageConsumer messageConsumer = new MessageConsumer(messageReceivedLatch, null);
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                argumentResolverService,
                queueProperties,
                amazonSqsAsync,
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
        sendNumberOfMessages(numberOfMessages, localSqsRule.getAmazonSqsAsync(), queueUrl);

        // act
        messageBroker.start();

        // assert
        messageReceivedLatch.await(60, SECONDS);
        assertThat(messageConsumer.numberOfTimesProcessed.get()).isEqualTo(numberOfMessages);

        // cleanup
        messageBroker.stop().get(4, SECONDS);
        assertNoMessagesInQueue(localSqsRule.getAmazonSqsAsync(), queueUrl);
    }

    @Test
    public void usingPrefetchingMessageRetrieverCanConsumeAllMessages() throws Exception {
        // arrange
        final int concurrencyLevel = 10;
        final int numberOfMessages = 300;
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl(queueUrl).build();
        final AmazonSQSAsync amazonSqsAsync = localSqsRule.getAmazonSqsAsync();
        final AsyncMessageRetriever messageRetriever = new PrefetchingMessageRetriever(
                amazonSqsAsync,
                queueProperties,
                PrefetchingProperties
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
        final ArgumentResolverService argumentResolverService = new DefaultArgumentResolverService(payloadMapper, amazonSqsAsync);
        final MessageConsumer messageConsumer = new MessageConsumer(messageReceivedLatch, null);
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                argumentResolverService,
                queueProperties,
                amazonSqsAsync,
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
        sendNumberOfMessages(numberOfMessages, localSqsRule.getAmazonSqsAsync(), queueUrl);
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
        assertNoMessagesInQueue(localSqsRule.getAmazonSqsAsync(), queueUrl);
    }

    public static class MessageConsumer {
        private final CountDownLatch messagesReceivedLatch;
        private final CountDownLatch testCompletedLatch;
        private final AtomicInteger numberOfTimesProcessed = new AtomicInteger(0);

        public MessageConsumer(final CountDownLatch messagesReceivedLatch, final CountDownLatch testCompletedLatch) {
            this.messagesReceivedLatch = messagesReceivedLatch;
            this.testCompletedLatch = testCompletedLatch;
        }

        @SuppressWarnings("unused")
        public void consume(@Payload final String messagePayload) throws InterruptedException {
            numberOfTimesProcessed.incrementAndGet();
            messagesReceivedLatch.countDown();
            if (testCompletedLatch != null) {
                testCompletedLatch.await(1, SECONDS);
            }
        }
    }
}
