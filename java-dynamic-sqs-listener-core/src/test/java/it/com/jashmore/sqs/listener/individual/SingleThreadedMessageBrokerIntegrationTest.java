package it.com.jashmore.sqs.listener.individual;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DefaultArgumentResolverService;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.broker.singlethread.SingleThreadedMessageBroker;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.individual.IndividualMessageRetriever;
import com.jashmore.sqs.retriever.individual.IndividualMessageRetrieverProperties;
import com.jashmore.sqs.test.LocalSqsRule;
import it.com.jashmore.sqs.AbstractSqsIntegrationTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(Parameterized.class)
public class SingleThreadedMessageBrokerIntegrationTest extends AbstractSqsIntegrationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Rule
    public final LocalSqsRule localSqsRule = new LocalSqsRule();


    @Parameterized.Parameters
    public static Object[][] data() {
        return new Object[10][0];
    }

    private String queueUrl;

    @Before
    public void setUp() {
        queueUrl = localSqsRule.createRandomQueue();
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
        final CountDownLatch messageReceivedLatch = new CountDownLatch(numberOfMessages);
        final PayloadMapper payloadMapper = new JacksonPayloadMapper(OBJECT_MAPPER);
        final ArgumentResolverService argumentResolverService = new DefaultArgumentResolverService(payloadMapper, sqsAsyncClient);
        final MessageConsumer messageConsumer = new MessageConsumer(messageReceivedLatch, null);
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                argumentResolverService,
                queueProperties,
                sqsAsyncClient,
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
