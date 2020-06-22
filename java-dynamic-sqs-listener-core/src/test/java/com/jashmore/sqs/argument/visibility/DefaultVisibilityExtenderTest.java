package com.jashmore.sqs.argument.visibility;

import static com.jashmore.sqs.processor.argument.VisibilityExtender.DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import org.junit.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@ExtendWith(MockitoExtension.class)
class DefaultVisibilityExtenderTest {
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties
            .builder()
            .queueUrl("queueUrl")
            .build();
    private static final String RECEIPT_HANDLE = "receipt_handle";

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private CompletableFuture<ChangeMessageVisibilityResponse> changeMessageVisibilityResultFuture;

    private final Message message = Message.builder().receiptHandle(RECEIPT_HANDLE).build();

    private DefaultVisibilityExtender defaultVisibilityExtender;

    @BeforeEach
    void setUp() {
        defaultVisibilityExtender = new DefaultVisibilityExtender(sqsAsyncClient, QUEUE_PROPERTIES, message);
    }

    @Test
    void defaultExtendShouldIncreaseVisibilityByDefaultAmount() {
        // act
        defaultVisibilityExtender.extend();

        // assert
        verify(sqsAsyncClient).changeMessageVisibility(ChangeMessageVisibilityRequest
                .builder()
                .queueUrl("queueUrl")
                .receiptHandle(RECEIPT_HANDLE)
                .visibilityTimeout(DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS)
                .build());
    }

    @Test
    void extendShouldIncreaseVisibilityByAmountSet() {
        // act
        defaultVisibilityExtender.extend(10);

        // assert
        verify(sqsAsyncClient).changeMessageVisibility(ChangeMessageVisibilityRequest
                .builder()
                .queueUrl("queueUrl")
                .receiptHandle(RECEIPT_HANDLE)
                .visibilityTimeout(10)
                .build());
    }

    @Test
    void defaultExtendShouldReturnFutureFromAmazon() {
        // arrange
        when(sqsAsyncClient.changeMessageVisibility(ChangeMessageVisibilityRequest
                .builder()
                .queueUrl("queueUrl")
                .receiptHandle(RECEIPT_HANDLE)
                .visibilityTimeout(DEFAULT_VISIBILITY_EXTENSION_IN_SECONDS)
                .build()))
                .thenReturn(changeMessageVisibilityResultFuture);

        // act
        final Future<?> extendFuture = defaultVisibilityExtender.extend();

        // assert
        assertThat(extendFuture).isEqualTo(changeMessageVisibilityResultFuture);
    }


    @Test
    void extendShouldReturnFutureFromAmazon() {
        // arrange
        when(sqsAsyncClient.changeMessageVisibility(ChangeMessageVisibilityRequest
                .builder()
                .queueUrl("queueUrl")
                .receiptHandle(RECEIPT_HANDLE)
                .visibilityTimeout(10)
                .build()))
                .thenReturn(changeMessageVisibilityResultFuture);

        // act
        final Future<?> extendFuture = defaultVisibilityExtender.extend(10);

        // assert
        assertThat(extendFuture).isEqualTo(changeMessageVisibilityResultFuture);
    }
}
