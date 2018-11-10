package com.jashmore.sqs.argument.heartbeat;

import static com.jashmore.sqs.argument.heartbeat.Heartbeat.DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityResult;
import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.QueueProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.Future;

public class DefaultHeartbeatTest {
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties
            .builder()
            .queueUrl("queueUrl")
            .build();
    private static final String RECEIPT_HANDLE = "receipt_handle";

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AmazonSQSAsync amazonSqsAsync;

    @Mock
    private Future<ChangeMessageVisibilityResult> changeMessageVisibilityResultFuture;

    private final Message message = new Message()
            .withReceiptHandle(RECEIPT_HANDLE);

    private DefaultHeartbeat defaultHeartbeat;

    @Before
    public void setUp() {
        defaultHeartbeat = new DefaultHeartbeat(amazonSqsAsync, QUEUE_PROPERTIES, message);
    }

    @Test
    public void defaultHeartbeatShouldIncreaseVisibilityByDefaultAmount() {
        // act
        defaultHeartbeat.beat();

        // assert
        verify(amazonSqsAsync).changeMessageVisibilityAsync("queueUrl", RECEIPT_HANDLE, DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS);
    }

    @Test
    public void heartbeatShouldIncreaseVisibilityByAmountSet() {
        // act
        defaultHeartbeat.beat(10);

        // assert
        verify(amazonSqsAsync).changeMessageVisibilityAsync("queueUrl", RECEIPT_HANDLE, 10);
    }

    @Test
    public void defaultBeatShouldReturnFutureFromAmazon() {
        // arrange
        when(amazonSqsAsync.changeMessageVisibilityAsync("queueUrl", RECEIPT_HANDLE, DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS))
                .thenReturn(changeMessageVisibilityResultFuture);

        // act
        final Future<?> beatFuture = defaultHeartbeat.beat();

        // assert
        assertThat(beatFuture).isEqualTo(changeMessageVisibilityResultFuture);
    }


    @Test
    public void beatShouldReturnFutureFromAmazon() {
        // arrange
        when(amazonSqsAsync.changeMessageVisibilityAsync("queueUrl", RECEIPT_HANDLE, 10))
                .thenReturn(changeMessageVisibilityResultFuture);

        // act
        final Future<?> beatFuture = defaultHeartbeat.beat(10);

        // assert
        assertThat(beatFuture).isEqualTo(changeMessageVisibilityResultFuture);
    }
}
