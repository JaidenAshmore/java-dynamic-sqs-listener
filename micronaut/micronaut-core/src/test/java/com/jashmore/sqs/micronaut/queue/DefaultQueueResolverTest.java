package com.jashmore.sqs.micronaut.queue;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultQueueResolverTest {

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private Environment environment;

    private DefaultQueueResolver defaultQueueResolver;

    @BeforeEach
    void setUp() {
        defaultQueueResolver = new DefaultQueueResolver(environment);
    }

    @Test
    void resolvedValueThatIsAUrlIsReturned() {
        // arrange
        when(environment.getPlaceholderResolver()).thenReturn(mock(PropertyPlaceholderResolver.class));
        when(environment.getPlaceholderResolver().resolveRequiredPlaceholders("${variable}")).thenReturn("http://url");

        // act
        final String queueUrl = defaultQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}");

        // assert
        assertThat(queueUrl).isEqualTo("http://url");
    }

    @Test
    void resolvedValueThatIsNotAUrlCallsOutToAmazonForUrl() {
        // arrange
        when(environment.getPlaceholderResolver()).thenReturn(mock(PropertyPlaceholderResolver.class));
        when(environment.getPlaceholderResolver().resolveRequiredPlaceholders("${variable}")).thenReturn("someQueueName");
        when(sqsAsyncClient.getQueueUrl(ArgumentMatchers.<Consumer<GetQueueUrlRequest.Builder>>any()))
            .thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("http://url").build()));

        // act
        final String queueUrl = defaultQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}");

        // assert
        assertThat(queueUrl).isEqualTo("http://url");
    }

    @Test
    void exceptionThrownWhileGettingQueueUrlBubblesException() {
        // arrange
        when(environment.getPlaceholderResolver()).thenReturn(mock(PropertyPlaceholderResolver.class));
        when(environment.getPlaceholderResolver().resolveRequiredPlaceholders("${variable}")).thenReturn("someQueueName");
        final RuntimeException exceptionCause = new RuntimeException("error");
        when(sqsAsyncClient.getQueueUrl(ArgumentMatchers.<Consumer<GetQueueUrlRequest.Builder>>any()))
            .thenReturn(
                new CompletableFuture<GetQueueUrlResponse>() {
                    @Override
                    public GetQueueUrlResponse get() throws ExecutionException {
                        throw new ExecutionException(exceptionCause);
                    }
                }
            );

        // act
        final QueueResolutionException exception = assertThrows(
            QueueResolutionException.class,
            () -> defaultQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}")
        );

        // assert
        assertThat(exception).hasCause(exceptionCause);
    }

    @Test
    void interruptedThreadWhileGettingQueueUrlThrowsQueueResolutionException() {
        // arrange
        when(environment.getPlaceholderResolver()).thenReturn(mock(PropertyPlaceholderResolver.class));
        when(environment.getPlaceholderResolver().resolveRequiredPlaceholders("${variable}")).thenReturn("someQueueName");
        final InterruptedException exceptionCause = new InterruptedException();
        when(sqsAsyncClient.getQueueUrl(ArgumentMatchers.<Consumer<GetQueueUrlRequest.Builder>>any()))
            .thenReturn(
                new CompletableFuture<GetQueueUrlResponse>() {
                    @Override
                    public GetQueueUrlResponse get() throws ExecutionException {
                        throw new ExecutionException(exceptionCause);
                    }
                }
            );

        // act
        final QueueResolutionException exception = assertThrows(
            QueueResolutionException.class,
            () -> defaultQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}")
        );

        // assert
        assertThat(exception).hasCause(exceptionCause);
    }

    @Test
    void interruptedThreadWhileGettingQueueUrlShouldMaintainThreadInterruptedBoolean() {
        // arrange
        when(environment.getPlaceholderResolver()).thenReturn(mock(PropertyPlaceholderResolver.class));
        when(environment.getPlaceholderResolver().resolveRequiredPlaceholders("${variable}")).thenReturn("someQueueName");
        final InterruptedException interruptedException = new InterruptedException();
        when(sqsAsyncClient.getQueueUrl(ArgumentMatchers.<Consumer<GetQueueUrlRequest.Builder>>any()))
            .thenReturn(
                new CompletableFuture<GetQueueUrlResponse>() {
                    @Override
                    public GetQueueUrlResponse get() throws InterruptedException {
                        throw interruptedException;
                    }
                }
            );

        // act
        assertThrows(QueueResolutionException.class, () -> defaultQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}"));

        // assert
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
