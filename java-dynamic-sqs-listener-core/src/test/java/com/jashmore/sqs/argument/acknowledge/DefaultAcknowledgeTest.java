package com.jashmore.sqs.argument.acknowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.DeleteMessageResult;
import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.QueueProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

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
    private AmazonSQSAsync amazonSqsAsync;

    @Mock
    private Future<DeleteMessageResult> deleteMessageResultFuture;

    private final Message message = new Message().withReceiptHandle(RECEIPT_HANDLE);

    private DefaultAcknowledge defaultAcknowledge;

    @Before
    public void setUp() {
        defaultAcknowledge = new DefaultAcknowledge(amazonSqsAsync, QUEUE_PROPERTIES, message);
    }

    @Test
    public void whenAcknowledgedTheMessageShouldBeDeleted() {
        // arrange
        when(amazonSqsAsync.deleteMessageAsync("queueUrl", RECEIPT_HANDLE)).thenReturn(deleteMessageResultFuture);

        // act
        final Future<?> acknowledgeFuture = defaultAcknowledge.acknowledgeSuccessful();

        // assert
        assertThat(acknowledgeFuture).isEqualTo(deleteMessageResultFuture);
    }
}
