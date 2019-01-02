package com.jashmore.sqs.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DefaultQueueResolverServiceTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private Environment environment;

    private DefaultQueueResolverService defaultQueueResolverService;

    @Before
    public void setUp() {
        defaultQueueResolverService = new DefaultQueueResolverService(sqsAsyncClient, environment);
    }

    @Test
    public void resolvedValueThatIsAUrlIsReturned() throws InterruptedException {
        // arrange
        when(environment.resolveRequiredPlaceholders("${variable}")).thenReturn("http://url");

        // act
        final String queueUrl = defaultQueueResolverService.resolveQueueUrl("${variable}");

        // assert
        assertThat(queueUrl).isEqualTo("http://url");
    }

    @Test
    public void resolvedValueThatIsNotAUrlCallsOutToAmazonForUrl() throws InterruptedException {
        // arrange
        when(environment.resolveRequiredPlaceholders("${variable}")).thenReturn("someQueueName");
        when(sqsAsyncClient.getQueueUrl(Matchers.<Consumer<GetQueueUrlRequest.Builder>>any()))
                .thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("http://url").build()));

        // act
        final String queueUrl = defaultQueueResolverService.resolveQueueUrl("${variable}");

        // assert
        assertThat(queueUrl).isEqualTo("http://url");
    }
}
