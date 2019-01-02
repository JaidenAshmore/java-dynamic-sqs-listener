package com.jashmore.sqs.container.basic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.queue.QueueResolverService;
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
public class QueueListenerWrapperTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ArgumentResolverService argumentResolverService;

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private QueueResolverService queueResolver;

    private QueueListenerWrapper queueListenerWrapper;

    @Before
    public void setUp() {
        queueListenerWrapper = new QueueListenerWrapper(argumentResolverService, sqsAsyncClient, queueResolver);
    }

    @Test
    public void queueListenerWrapperCanBuildMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        final Object bean = new QueueListenerWrapperTest();
        final Method method = QueueListenerWrapperTest.class.getMethod("myMethod");

        // act
        final MessageListenerContainer messageListenerContainer = queueListenerWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer).isInstanceOf(SimpleMessageListenerContainer.class);
    }

    @Test
    public void queueIsResolvedViaTheQueueResolverService() throws NoSuchMethodException, InterruptedException {
        // arrange
        final Object bean = new QueueListenerWrapperTest();
        final Method method = QueueListenerWrapperTest.class.getMethod("myMethod");

        // act
        queueListenerWrapper.wrapMethod(bean, method);

        // assert
        verify(queueResolver).resolveQueueUrl("test");
    }

    @QueueListener("test")
    public void myMethod() {

    }
}
