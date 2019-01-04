package com.jashmore.sqs.argument.acknowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class DefaultAcknowledgeTest {
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties
            .builder()
            .queueUrl("queueUrl")
            .build();
    private static final String RECEIPT_HANDLE = "test_receipt_handle";

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private CompletableFuture<DeleteMessageResponse> deleteMessageResultFuture;

    private final Message message = Message.builder().receiptHandle(RECEIPT_HANDLE).build();

    private DefaultAcknowledge defaultAcknowledge;

    @Before
    public void setUp() {
        defaultAcknowledge = new DefaultAcknowledge(sqsAsyncClient, QUEUE_PROPERTIES, message);
    }

    @Test
    public void whenAcknowledgedTheMessageShouldBeDeleted() {
        // arrange
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder().queueUrl("queueUrl").receiptHandle(RECEIPT_HANDLE).build();
        when(sqsAsyncClient.deleteMessage(deleteMessageRequest)).thenReturn(deleteMessageResultFuture);

        // act
        final Future<?> acknowledgeFuture = defaultAcknowledge.acknowledgeSuccessful();

        // assert
        assertThat(acknowledgeFuture).isEqualTo(deleteMessageResultFuture);
    }
}
