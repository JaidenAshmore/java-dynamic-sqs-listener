package com.jashmore.sqs.spring.container.prefetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.spring.container.MessageListenerContainer;
import com.jashmore.sqs.spring.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.spring.queue.QueueResolverService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.Method;

/**
 * Class is hard to test as it is the one building all of the dependencies internally using new constructors. Don't really know a better way to do this
 * without building unnecessary classes.
 */
public class PrefetchingQueueListenerWrapperTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ArgumentResolverService argumentResolverService;

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private QueueResolverService queueResolver;

    private PrefetchingQueueListenerWrapper prefetchingQueueListenerWrapper;

    @Before
    public void setUp() {
        prefetchingQueueListenerWrapper = new PrefetchingQueueListenerWrapper(argumentResolverService, sqsAsyncClient, queueResolver);
    }

    @Test
    public void canBuildMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("myMethod");

        // act
        final MessageListenerContainer messageListenerContainer = prefetchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer).isInstanceOf(SimpleMessageListenerContainer.class);
    }

    @Test
    public void queueIsResolvedViaTheQueueResolverService() throws NoSuchMethodException {
        // arrange
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("myMethod");

        // act
        prefetchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        verify(queueResolver).resolveQueueUrl("test");
    }

    @PrefetchingQueueListener("test")
    public void myMethod() {

    }
}
