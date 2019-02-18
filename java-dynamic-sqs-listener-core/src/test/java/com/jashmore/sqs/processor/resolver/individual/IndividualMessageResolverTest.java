package com.jashmore.sqs.processor.resolver.individual;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.jashmore.sqs.QueueProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.LoggerFactory;
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

    @Mock
    private Appender<ILoggingEvent> mockLoggingAppender;

    private IndividualMessageResolver individualMessageResolver;

    @Before
    public void setUp() {
        individualMessageResolver = new IndividualMessageResolver(QUEUE_PROPERTIES, sqsAsyncClient);

        when(mockLoggingAppender.getName()).thenReturn("MOCK");
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(mockLoggingAppender);
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

    @Test
    public void successfulResolvingOfMessageWillLogDebugMessage() {
        // arrange
        when(sqsAsyncClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));

        // act
        individualMessageResolver.resolveMessage(Message.builder().messageId("id").body("test").receiptHandle("receipt").build());

        // assert
        verify(mockLoggingAppender).doAppend(argThat(((ArgumentMatcher<LoggingEvent>) argument -> argument.getLevel().equals(Level.DEBUG)
                && argument.getFormattedMessage().contains("Message successfully deleted: id"))));
    }

    @Test
    public void failureToResolveMessagesResultsInErrorMessage() {
        // arrange
        final RuntimeException exception = new RuntimeException("exception");
        when(sqsAsyncClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenAnswer(invocation -> {
                    final CompletableFuture<?> future = new CompletableFuture<>();
                    future.completeExceptionally(exception);
                    return future;
                });

        // act
        individualMessageResolver.resolveMessage(Message.builder().messageId("id").body("test").receiptHandle("receipt").build());

        // assert
        verify(mockLoggingAppender).doAppend(argThat(((ArgumentMatcher<LoggingEvent>) argument -> argument.getLevel().equals(Level.ERROR)
                && argument.getFormattedMessage().contains("Error deleting message: id"))));
    }
}