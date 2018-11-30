package com.jashmore.sqs.argument.visibility;

import static com.jashmore.sqs.argument.visibility.VisibilityExtender.DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS;
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

public class DefaultVisibilityExtenderTest {
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

    private DefaultVisibilityExtender defaultVisibilityExtender;

    @Before
    public void setUp() {
        defaultVisibilityExtender = new DefaultVisibilityExtender(amazonSqsAsync, QUEUE_PROPERTIES, message);
    }

    @Test
    public void defaultExtendShouldIncreaseVisibilityByDefaultAmount() {
        // act
        defaultVisibilityExtender.extend();

        // assert
        verify(amazonSqsAsync).changeMessageVisibilityAsync("queueUrl", RECEIPT_HANDLE, DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS);
    }

    @Test
    public void extendShouldIncreaseVisibilityByAmountSet() {
        // act
        defaultVisibilityExtender.extend(10);

        // assert
        verify(amazonSqsAsync).changeMessageVisibilityAsync("queueUrl", RECEIPT_HANDLE, 10);
    }

    @Test
    public void defaultExtendShouldReturnFutureFromAmazon() {
        // arrange
        when(amazonSqsAsync.changeMessageVisibilityAsync("queueUrl", RECEIPT_HANDLE, DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS))
                .thenReturn(changeMessageVisibilityResultFuture);

        // act
        final Future<?> extendFuture = defaultVisibilityExtender.extend();

        // assert
        assertThat(extendFuture).isEqualTo(changeMessageVisibilityResultFuture);
    }


    @Test
    public void extendShouldReturnFutureFromAmazon() {
        // arrange
        when(amazonSqsAsync.changeMessageVisibilityAsync("queueUrl", RECEIPT_HANDLE, 10))
                .thenReturn(changeMessageVisibilityResultFuture);

        // act
        final Future<?> extendFuture = defaultVisibilityExtender.extend(10);

        // assert
        assertThat(extendFuture).isEqualTo(changeMessageVisibilityResultFuture);
    }
}
