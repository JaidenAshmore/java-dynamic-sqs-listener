package com.jashmore.sqs.spring.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class DefaultQueueResolverTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private Environment environment;

    private DefaultQueueResolver defaultQueueResolver;

    @Before
    public void setUp() {
        defaultQueueResolver = new DefaultQueueResolver(environment);
    }

    @After
    public void tearDown() {
        // If the thread running the tests is interrupted it will break future tests. This will be fixed in release of JUnit 4.13 but until then
        // we use this workaround. See https://github.com/junit-team/junit4/issues/1365
        Thread.interrupted();
    }

    @Test
    public void resolvedValueThatIsAUrlIsReturned() {
        // arrange
        when(environment.resolveRequiredPlaceholders("${variable}")).thenReturn("http://url");

        // act
        final String queueUrl = defaultQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}");

        // assert
        assertThat(queueUrl).isEqualTo("http://url");
    }

    @Test
    public void resolvedValueThatIsNotAUrlCallsOutToAmazonForUrl() {
        // arrange
        when(environment.resolveRequiredPlaceholders("${variable}")).thenReturn("someQueueName");
        when(sqsAsyncClient.getQueueUrl(ArgumentMatchers.<Consumer<GetQueueUrlRequest.Builder>>any()))
                .thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("http://url").build()));

        // act
        final String queueUrl = defaultQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}");

        // assert
        assertThat(queueUrl).isEqualTo("http://url");
    }

    @Test
    public void exceptionThrownWhileGettingQueueUrlBubblesException() {
        // arrange
        when(environment.resolveRequiredPlaceholders("${variable}")).thenReturn("someQueueName");
        final RuntimeException exceptionCause = new RuntimeException("error");
        when(sqsAsyncClient.getQueueUrl(ArgumentMatchers.<Consumer<GetQueueUrlRequest.Builder>>any()))
                .thenReturn(new CompletableFuture<GetQueueUrlResponse>() {
                    @Override
                    public GetQueueUrlResponse get() throws ExecutionException {
                        throw new ExecutionException(exceptionCause);
                    }
                });
        expectedException.expect(instanceOf(QueueResolutionException.class));
        expectedException.expectCause(equalTo(exceptionCause));

        // act
        defaultQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}");
    }

    @Test
    public void interruptedThreadWhileGettingQueueUrlThrowsQueueResolutionException() {
        // arrange
        when(environment.resolveRequiredPlaceholders("${variable}")).thenReturn("someQueueName");
        final InterruptedException exceptionCause = new InterruptedException();
        when(sqsAsyncClient.getQueueUrl(ArgumentMatchers.<Consumer<GetQueueUrlRequest.Builder>>any()))
                .thenReturn(new CompletableFuture<GetQueueUrlResponse>() {
                    @Override
                    public GetQueueUrlResponse get() throws ExecutionException {
                        throw new ExecutionException(exceptionCause);
                    }
                });
        expectedException.expect(instanceOf(QueueResolutionException.class));
        expectedException.expectCause(equalTo(exceptionCause));

        // act
        defaultQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}");
    }

    @Test
    public void interruptedThreadWhileGettingQueueUrlShouldMaintainThreadInterruptedBoolean() {
        // arrange
        when(environment.resolveRequiredPlaceholders("${variable}")).thenReturn("someQueueName");
        final InterruptedException interruptedException = new InterruptedException();
        when(sqsAsyncClient.getQueueUrl(ArgumentMatchers.<Consumer<GetQueueUrlRequest.Builder>>any()))
                .thenReturn(new CompletableFuture<GetQueueUrlResponse>() {
                    @Override
                    public GetQueueUrlResponse get() throws InterruptedException {
                        throw interruptedException;
                    }
                });

        // act
        try {
            defaultQueueResolver.resolveQueueUrl(sqsAsyncClient, "${variable}");
        } catch (final QueueResolutionException exception) {
            // assert
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
    }
}
