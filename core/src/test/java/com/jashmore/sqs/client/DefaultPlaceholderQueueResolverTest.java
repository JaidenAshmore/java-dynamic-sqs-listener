package com.jashmore.sqs.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.placeholder.PlaceholderResolver;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

@ExtendWith(MockitoExtension.class)
class DefaultPlaceholderQueueResolverTest {

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private PlaceholderResolver placeholderResolver;

    private DefaultPlaceholderQueueResolver defaultPlaceholderQueueResolver;

    @BeforeEach
    void setUp() {
        defaultPlaceholderQueueResolver = new DefaultPlaceholderQueueResolver(placeholderResolver);
    }

    @Test
    void resolvedValueThatIsAUrlIsReturned() {
        // arrange
        when(placeholderResolver.resolvePlaceholders("${variable}")).thenReturn("http://url");

        // act
        final String queueUrl = defaultPlaceholderQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}");

        // assert
        assertThat(queueUrl).isEqualTo("http://url");
    }

    @Test
    void resolvedValueThatIsNotAUrlCallsOutToAmazonForUrl() {
        // arrange
        when(placeholderResolver.resolvePlaceholders("${variable}")).thenReturn("someQueueName");
        when(sqsAsyncClient.getQueueUrl(ArgumentMatchers.<Consumer<GetQueueUrlRequest.Builder>>any()))
            .thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("http://url").build()));

        // act
        final String queueUrl = defaultPlaceholderQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}");

        // assert
        assertThat(queueUrl).isEqualTo("http://url");
    }

    @Test
    void exceptionThrownWhileGettingQueueUrlBubblesException() {
        // arrange
        when(placeholderResolver.resolvePlaceholders("${variable}")).thenReturn("someQueueName");
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
            () -> defaultPlaceholderQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}")
        );

        // assert
        assertThat(exception).hasCause(exceptionCause);
    }

    @Test
    void interruptedThreadWhileGettingQueueUrlThrowsQueueResolutionException() {
        // arrange
        when(placeholderResolver.resolvePlaceholders("${variable}")).thenReturn("someQueueName");
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
            () -> defaultPlaceholderQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}")
        );

        // assert
        assertThat(exception).hasCause(exceptionCause);
    }

    @Test
    void interruptedThreadWhileGettingQueueUrlShouldMaintainThreadInterruptedBoolean() {
        // arrange
        when(placeholderResolver.resolvePlaceholders("${variable}")).thenReturn("someQueueName");
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
        assertThrows(QueueResolutionException.class, () -> defaultPlaceholderQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}"));

        // assert
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
