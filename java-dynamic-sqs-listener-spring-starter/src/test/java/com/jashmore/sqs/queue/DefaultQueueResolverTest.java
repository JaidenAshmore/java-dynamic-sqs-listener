package com.jashmore.sqs.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.core.env.Environment;

public class DefaultQueueResolverTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AmazonSQSAsync amazonSqsAsync;

    @Mock
    private Environment environment;

    private DefaultQueueResolver defaultQueueResolver;

    @Before
    public void setUp() {
        defaultQueueResolver = new DefaultQueueResolver(amazonSqsAsync, environment);
    }

    @Test
    public void resolvedValueThatIsAUrlIsReturned() {
        // arrange
        when(environment.resolveRequiredPlaceholders("${variable}")).thenReturn("http://url");

        // act
        final String queueUrl = defaultQueueResolver.resolveQueueUrl("${variable}");

        // assert
        assertThat(queueUrl).isEqualTo("http://url");
    }

    @Test
    public void resolvedValueThatIsNotAUrlCallsOutToAmazonForUrl() {
        // arrange
        when(environment.resolveRequiredPlaceholders("${variable}")).thenReturn("someQueueName");
        when(amazonSqsAsync.getQueueUrl("someQueueName")).thenReturn(new GetQueueUrlResult().withQueueUrl("http://url"));

        // act
        final String queueUrl = defaultQueueResolver.resolveQueueUrl("${variable}");

        // assert
        assertThat(queueUrl).isEqualTo("http://url");
    }
}
