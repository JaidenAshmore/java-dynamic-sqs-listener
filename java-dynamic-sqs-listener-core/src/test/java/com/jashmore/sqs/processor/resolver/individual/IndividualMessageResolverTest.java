package com.jashmore.sqs.processor.resolver.individual;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

public class IndividualMessageResolverTest {
    private static final QueueProperties QUEUE_PROPERTIES = QueueProperties.builder()
            .queueUrl("queueUrl")
            .build();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    private IndividualMessageResolver individualMessageResolver;

    @Before
    public void setUp() {
        individualMessageResolver = new IndividualMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient);
    }

    @Test
    public void resolvingMessageWillDeleteItFromQueueStraightAway() {
        // arrange
        when(sqsAsyncClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));

        // act
        individualMessageResolver.resolveMessage(Message.builder().messageId("id").body("test").receiptHandle("receipt").build());

        // assert
        verify(sqsAsyncClient).deleteMessage(DeleteMessageRequest.builder()
                .queueUrl("queueUrl")
                .receiptHandle("receipt")
                .build());
    }

    @Test
    public void failureToResolveMessageStillAllowsForFutureMessageResolution() {
        // arrange
        when(sqsAsyncClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenAnswer(invocation -> {
                    final CompletableFuture<?> future = new CompletableFuture<>();
                    future.completeExceptionally(new RuntimeException("exception"));
                    return future;
                })
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));

        // act
        individualMessageResolver.resolveMessage(Message.builder().messageId("id").body("test").receiptHandle("receipt").build());
        individualMessageResolver.resolveMessage(Message.builder().messageId("id2").body("test").receiptHandle("receipt2").build());

        // assert
        verify(sqsAsyncClient).deleteMessage(DeleteMessageRequest.builder()
                .queueUrl("queueUrl")
                .receiptHandle("receipt2")
                .build());
    }
}