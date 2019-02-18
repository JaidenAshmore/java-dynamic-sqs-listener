package it.com.jashmore.sqs.listener.individual;

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
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.broker.singlethread.SingleThreadedMessageBroker;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.processor.resolver.individual.IndividualMessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.individual.IndividualMessageRetriever;
import com.jashmore.sqs.retriever.individual.IndividualMessageRetrieverProperties;
import com.jashmore.sqs.test.LocalSqsRule;
import it.com.jashmore.sqs.AbstractSqsIntegrationTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class SingleThreadedMessageBrokerIntegrationTest extends AbstractSqsIntegrationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Rule
    public final LocalSqsRule localSqsRule = new LocalSqsRule();

    private String queueUrl;

    @Before
    public void setUp() {
        queueUrl = localSqsRule.createRandomQueue();

        // If the thread running the tests is interrupted it will break future tests. This will be fixed in release of JUnit 4.13 but until then
        // we use this workaround. See https://github.com/junit-team/junit4/issues/1365
        Thread.interrupted();
    }

    @Test
    public void individualMessageListenerCanProcessAllMessagesOnQueue() throws Exception {
        // arrange
        final int numberOfMessages = 20;
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl(queueUrl).build();
        final SqsAsyncClient sqsAsyncClient = localSqsRule.getLocalAmazonSqsAsync();
        final MessageRetriever messageRetriever = new IndividualMessageRetriever(
                sqsAsyncClient,
                queueProperties,
                IndividualMessageRetrieverProperties.builder().visibilityTimeoutForMessagesInSeconds(1).build()
        );
        final IndividualMessageResolver individualMessageResolver = new IndividualMessageResolver(queueProperties, sqsAsyncClient);
        final CountDownLatch messageReceivedLatch = new CountDownLatch(numberOfMessages);
        final PayloadMapper payloadMapper = new JacksonPayloadMapper(OBJECT_MAPPER);
        final ArgumentResolverService argumentResolverService = new CoreArgumentResolverService(payloadMapper, sqsAsyncClient);
        final MessageConsumer messageConsumer = new MessageConsumer(messageReceivedLatch, null);
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                argumentResolverService,
                queueProperties,
                individualMessageResolver,
                MessageConsumer.class.getMethod("consume", String.class),
                messageConsumer
        );
        final MessageBroker container = new SingleThreadedMessageBroker(messageRetriever, messageProcessor);
        sendNumberOfMessages(numberOfMessages, sqsAsyncClient, queueUrl);

        // act
        container.start();

        // assert
        messageReceivedLatch.await(1, MINUTES);
        assertThat(messageConsumer.numberOfTimesProcessed.get()).isEqualTo(numberOfMessages);

        // cleanup
        container.stop().get(10, SECONDS);
        assertNoMessagesInQueue(sqsAsyncClient, queueUrl);
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
            Thread.sleep(100);
            if (testCompletedLatch != null) {
                testCompletedLatch.await(1, SECONDS);
            }
        }
    }
}
