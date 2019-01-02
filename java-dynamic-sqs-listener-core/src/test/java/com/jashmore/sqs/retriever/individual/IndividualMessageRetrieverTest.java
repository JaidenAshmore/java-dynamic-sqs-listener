package com.jashmore.sqs.retriever.individual;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class IndividualMessageRetrieverTest {
    private static final String QUEUE_URL = "queueUrl";
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder()
            .queueUrl(QUEUE_URL)
            .build();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SqsAsyncClient amazonSqs;

    @Mock
    private CompletableFuture<ReceiveMessageResponse> receiveMessageResultFuture;

    @Test
    public void retrievingMessageWithNoTimeoutKeepsCallingUntilRetrieved() throws ExecutionException, InterruptedException {
        final Message message = Message.builder().build();
        when(receiveMessageResultFuture.get())
                .thenReturn(ReceiveMessageResponse.builder().build())
                .thenReturn(ReceiveMessageResponse.builder().build())
                .thenReturn(ReceiveMessageResponse.builder().build())
                .thenReturn(ReceiveMessageResponse.builder().build())
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        when(amazonSqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(
                amazonSqs, QUEUE_PROPERTIES, IndividualMessageRetrieverProperties.builder().visibilityTimeoutForMessagesInSeconds(5).build());

        // act
        final Message messageRetrieved = retriever.retrieveMessage();

        // assert
        assertThat(messageRetrieved).isEqualTo(message);
        verify(amazonSqs, times(5)).receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
                .visibilityTimeout(5)
                .build()
        );
    }

    @Test
    public void retrievingMessageThatThrowsExecutionExceptionWrapsCause() throws ExecutionException, InterruptedException {
        final Throwable cause = new Throwable();
        when(receiveMessageResultFuture.get())
                .thenReturn(ReceiveMessageResponse.builder().build())
                .thenThrow(new ExecutionException("error", cause));
        when(amazonSqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
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
