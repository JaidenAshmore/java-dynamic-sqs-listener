package com.jashmore.sqs.retriever.individual;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkInterruptedException;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class IndividualMessageRetrieverTest {
    private static final String QUEUE_URL = "queueUrl";
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder()
            .queueUrl(QUEUE_URL)
            .build();
    private static final StaticIndividualMessageRetrieverProperties DEFAULT_PROPERTIES = StaticIndividualMessageRetrieverProperties.builder()
            .visibilityTimeoutForMessagesInSeconds(5)
            .errorBackoffTimeInMilliseconds(0L)
            .build();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private CompletableFuture<ReceiveMessageResponse> receiveMessageResultFuture;

    @Test
    public void retrievingMessageWillKeepTryingUntilMessageIsEventuallyDownloaded() throws ExecutionException, InterruptedException {
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final Message message = Message.builder().build();
        when(receiveMessageResultFuture.get())
                .thenReturn(ReceiveMessageResponse.builder().build())
                .thenReturn(ReceiveMessageResponse.builder().build())
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PROPERTIES);

        // act
        final Message messageRetrieved = retriever.retrieveMessage();

        // assert
        assertThat(messageRetrieved).isEqualTo(message);
        verify(sqsAsyncClient, times(3)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    public void messageRetrievalWillPassVisibilityTimeoutFromProperties() throws ExecutionException, InterruptedException {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final Message message = Message.builder().build();
        when(receiveMessageResultFuture.get())
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PROPERTIES);

        // act
        retriever.retrieveMessage();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isEqualTo(DEFAULT_PROPERTIES.getMessageVisibilityTimeoutInSeconds());
    }

    @Test
    public void noVisibilityTimeoutInPropertiesWillNotIncludeInRequestForMessages() throws ExecutionException, InterruptedException {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final Message message = Message.builder().build();
        when(receiveMessageResultFuture.get())
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        final StaticIndividualMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder()
                .visibilityTimeoutForMessagesInSeconds(null)
                .build();
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        retriever.retrieveMessage();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    public void negativeVisibilityTimeoutWillNotIncludeVisibilityTimeout() throws ExecutionException, InterruptedException {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final Message message = Message.builder().build();
        when(receiveMessageResultFuture.get())
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        final StaticIndividualMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder()
                .visibilityTimeoutForMessagesInSeconds(-1)
                .build();
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        retriever.retrieveMessage();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    public void zeroVisibilityTimeoutWillNotIncludeVisibilityTimeout() throws ExecutionException, InterruptedException {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final Message message = Message.builder().build();
        when(receiveMessageResultFuture.get())
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        final StaticIndividualMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder()
                .visibilityTimeoutForMessagesInSeconds(0)
                .build();
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        retriever.retrieveMessage();

        // assert
        final ArgumentCaptor<ReceiveMessageRequest> receiveMessageRequestArgumentCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsAsyncClient).receiveMessage(receiveMessageRequestArgumentCaptor.capture());
        assertThat(receiveMessageRequestArgumentCaptor.getValue().visibilityTimeout()).isNull();
    }

    @Test
    public void messageRetrievalWillBuildRequestUsingDefaultProperties() throws ExecutionException, InterruptedException {
        // arrange
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final Message message = Message.builder().build();
        when(receiveMessageResultFuture.get())
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PROPERTIES);

        // act
        retriever.retrieveMessage();

        // assert
        verify(sqsAsyncClient).receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .maxNumberOfMessages(1)
                .attributeNames(QueueAttributeName.ALL)
                .messageAttributeNames(QueueAttributeName.ALL.toString())
                .waitTimeSeconds(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
                .visibilityTimeout(5)
                .build()
        );
    }

    @Test
    public void retrievingMessageThatThrowsExecutionExceptionWillTryAgain() throws ExecutionException, InterruptedException {
        final Throwable cause = new Throwable();
        final Message message = Message.builder().build();
        when(receiveMessageResultFuture.get())
                .thenThrow(new ExecutionException("error", cause))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PROPERTIES);

        // act
        final Message retrievedMessage = retriever.retrieveMessage();

        // assert
        assertThat(retrievedMessage).isSameAs(message);
    }

    @Test
    public void retrievingMessageThatThrowsExecutionExceptionWillBackoffForPeriod() throws ExecutionException, InterruptedException {
        final Throwable cause = new Throwable();
        final Message message = Message.builder().build();
        when(receiveMessageResultFuture.get())
                .thenThrow(new ExecutionException("error", cause))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final IndividualMessageRetrieverProperties properties = DEFAULT_PROPERTIES.toBuilder()
                .errorBackoffTimeInMilliseconds(1000L)
                .build();
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, properties);

        // act
        final long timeNow = System.currentTimeMillis();
        retriever.retrieveMessage();
        final long timeRetrieved = System.currentTimeMillis();

        // assert
        assertThat(timeRetrieved - timeNow).isGreaterThanOrEqualTo(1000L);
    }

    @Test
    public void sdkInterruptedExceptionThrownBySqsAsyncClientWillThrowInterruptedException() throws ExecutionException, InterruptedException {
        doThrow(new ExecutionException(SdkClientException.builder().cause(new SdkInterruptedException()).build())).when(receiveMessageResultFuture).get();
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultFuture);
        final IndividualMessageRetriever retriever = new IndividualMessageRetriever(sqsAsyncClient, QUEUE_PROPERTIES, DEFAULT_PROPERTIES);
        expectedException.expect(InterruptedException.class);
        expectedException.expectMessage("Interrupted while retrieving messages");

        // act
        retriever.retrieveMessage();
    }
}
