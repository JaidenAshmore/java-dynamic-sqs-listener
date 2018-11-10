package com.jashmore.sqs.retriever.individual;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.jashmore.sqs.QueueProperties;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class IndividualMessageRetrieverTest {
    private static final String QUEUE_URL = "queueUrl";
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder()
            .queueUrl(QUEUE_URL)
            .build();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AmazonSQSAsync amazonSqs;

    @Mock
    private Future<ReceiveMessageResult> receiveMessageResultFuture;

    @Test
    public void retrieveMessagesWithTimeoutLessThanOneSecondCallsWithWaitZero() throws ExecutionException, InterruptedException {
        // arrange
        final Message message = new Message();
        when(receiveMessageResultFuture.get()).thenReturn(new ReceiveMessageResult().withMessages(message));
        when(amazonSqs.receiveMessageAsync(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(
                amazonSqs, QUEUE_PROPERTIES, IndividualMessageRetrieverProperties.builder().build());

        // act
        final Optional<Message> optionalMessage = retriever.retrieveMessage(15, MILLISECONDS);

        // assert
        assertThat(optionalMessage).contains(message);
        verify(amazonSqs).receiveMessageAsync(new ReceiveMessageRequest(QUEUE_URL)
                .withMaxNumberOfMessages(1)
                .withWaitTimeSeconds(0)
        );
    }

    @Test
    public void retrieveMessagesWithTimeoutWillHaveWaitTimeoutSameAsValueWhenBelow20Seconds() throws ExecutionException, InterruptedException {
        // arrange
        final Message message = new Message();
        when(receiveMessageResultFuture.get()).thenReturn(new ReceiveMessageResult().withMessages(message));
        when(amazonSqs.receiveMessageAsync(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(
                amazonSqs, QUEUE_PROPERTIES, IndividualMessageRetrieverProperties.builder().build());

        // act
        final Optional<Message> optionalMessage = retriever.retrieveMessage(15, SECONDS);

        // assert
        assertThat(optionalMessage).contains(message);
        verify(amazonSqs).receiveMessageAsync(new ReceiveMessageRequest(QUEUE_URL)
                .withMaxNumberOfMessages(1)
                .withWaitTimeSeconds(15)
        );
    }

    @Test
    public void retrieveMessagesWithTimeoutWithMillisecondsWithScaleToSecondsForWaitTime() throws ExecutionException, InterruptedException {
        // arrange
        final Message message = new Message();
        when(receiveMessageResultFuture.get()).thenReturn(new ReceiveMessageResult().withMessages(message));
        when(amazonSqs.receiveMessageAsync(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(
                amazonSqs, QUEUE_PROPERTIES, IndividualMessageRetrieverProperties.builder().build());

        // act
        final Optional<Message> optionalMessage = retriever.retrieveMessage(15400, MILLISECONDS); // 15.4 Seconds

        // assert
        assertThat(optionalMessage).contains(message);
        verify(amazonSqs).receiveMessageAsync(new ReceiveMessageRequest(QUEUE_URL)
                .withMaxNumberOfMessages(1)
                .withWaitTimeSeconds(15)
        );
    }

    @Test
    public void retrieveMessagesWithTimeoutGreaterThan20SecondsWillCallMultipleTimes() throws ExecutionException, InterruptedException {
        // arrange
        when(receiveMessageResultFuture.get())
                .thenReturn(new ReceiveMessageResult())
                .thenReturn(new ReceiveMessageResult())
                .thenReturn(new ReceiveMessageResult())
                .thenReturn(new ReceiveMessageResult());
        when(amazonSqs.receiveMessageAsync(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(
                amazonSqs, QUEUE_PROPERTIES, IndividualMessageRetrieverProperties.builder().build());

        // act
        final Optional<Message> optionalMessage = retriever.retrieveMessage(90, SECONDS);

        // assert
        assertThat(optionalMessage).isEmpty();
        verify(amazonSqs, times(4)).receiveMessageAsync(new ReceiveMessageRequest(QUEUE_URL)
                .withMaxNumberOfMessages(1)
                .withWaitTimeSeconds(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS));
        verify(amazonSqs, times(1)).receiveMessageAsync(new ReceiveMessageRequest(QUEUE_URL)
                .withMaxNumberOfMessages(1)
                .withWaitTimeSeconds(10));
    }

    @Test
    public void retrieveMessagesWithMultipleCallsReturnsWhenMessageReturned() throws ExecutionException, InterruptedException {
        // arrange
        final Message message = new Message();
        when(receiveMessageResultFuture.get())
                .thenReturn(new ReceiveMessageResult())
                .thenReturn(new ReceiveMessageResult().withMessages(message));
        when(amazonSqs.receiveMessageAsync(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(
                amazonSqs, QUEUE_PROPERTIES, IndividualMessageRetrieverProperties.builder().build());

        // act
        final Optional<Message> optionalMessage = retriever.retrieveMessage(90, SECONDS);

        // assert
        assertThat(optionalMessage).contains(message);
        verify(amazonSqs, times(2)).receiveMessageAsync(new ReceiveMessageRequest(QUEUE_URL)
                .withMaxNumberOfMessages(1)
                .withWaitTimeSeconds(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS));
    }

    @Test
    public void visibilityTimeoutIsPassedIntoAmazonCall() throws ExecutionException, InterruptedException {
        // arrange
        final Message message = new Message();
        when(receiveMessageResultFuture.get()).thenReturn(new ReceiveMessageResult().withMessages(message));
        when(amazonSqs.receiveMessageAsync(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(
                amazonSqs, QUEUE_PROPERTIES, IndividualMessageRetrieverProperties.builder().visibilityTimeoutForMessagesInSeconds(5).build());

        // act
        final Optional<Message> optionalMessage = retriever.retrieveMessage(15, SECONDS);

        // assert
        assertThat(optionalMessage).contains(message);
        verify(amazonSqs).receiveMessageAsync(new ReceiveMessageRequest(QUEUE_URL)
                .withMaxNumberOfMessages(1)
                .withWaitTimeSeconds(15)
                .withVisibilityTimeout(5)
        );
    }

    @Test
    public void retrievingMessageNowRequestsWithZeroWaitTime() throws ExecutionException, InterruptedException {
        final Message message = new Message();
        when(receiveMessageResultFuture.get()).thenReturn(new ReceiveMessageResult().withMessages(message));
        when(amazonSqs.receiveMessageAsync(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(
                amazonSqs, QUEUE_PROPERTIES, IndividualMessageRetrieverProperties.builder().visibilityTimeoutForMessagesInSeconds(5).build());

        // act
        final Optional<Message> optionalMessage = retriever.retrieveMessageNow();

        // assert
        assertThat(optionalMessage).contains(message);
        verify(amazonSqs).receiveMessageAsync(new ReceiveMessageRequest(QUEUE_URL)
                .withMaxNumberOfMessages(1)
                .withWaitTimeSeconds(0)
                .withVisibilityTimeout(5)
        );
    }

    @Test
    public void retrievingMessageWithNoTimeoutKeepsCallingUntilRetrieved() throws ExecutionException, InterruptedException {
        final Message message = new Message();
        when(receiveMessageResultFuture.get())
                .thenReturn(new ReceiveMessageResult())
                .thenReturn(new ReceiveMessageResult())
                .thenReturn(new ReceiveMessageResult())
                .thenReturn(new ReceiveMessageResult())
                .thenReturn(new ReceiveMessageResult().withMessages(message));
        when(amazonSqs.receiveMessageAsync(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(
                amazonSqs, QUEUE_PROPERTIES, IndividualMessageRetrieverProperties.builder().visibilityTimeoutForMessagesInSeconds(5).build());

        // act
        final Message messageRetrieved = retriever.retrieveMessage();

        // assert
        assertThat(messageRetrieved).isEqualTo(message);
        verify(amazonSqs, times(5)).receiveMessageAsync(new ReceiveMessageRequest(QUEUE_URL)
                .withMaxNumberOfMessages(1)
                .withWaitTimeSeconds(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
                .withVisibilityTimeout(5)
        );
    }

    @Test
    public void retrievingMessageThatThrowsExecutionExceptionWrapsCause() throws ExecutionException, InterruptedException {
        final Throwable cause = new Throwable();
        when(receiveMessageResultFuture.get())
                .thenReturn(new ReceiveMessageResult())
                .thenThrow(new ExecutionException("error", cause));
        when(amazonSqs.receiveMessageAsync(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(
                amazonSqs, QUEUE_PROPERTIES, IndividualMessageRetrieverProperties.builder().visibilityTimeoutForMessagesInSeconds(5).build());

        // act
        try {
            retriever.retrieveMessage();
            fail("Should have thrown a exception");
        } catch (final Exception exception) {
            // assert
            assertThat(exception).isInstanceOfAny(RuntimeException.class);
            assertThat(exception).hasCause(cause);
        }
    }
}
