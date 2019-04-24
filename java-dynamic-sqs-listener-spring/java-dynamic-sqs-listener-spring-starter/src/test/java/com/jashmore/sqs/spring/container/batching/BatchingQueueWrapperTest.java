package com.jashmore.sqs.spring.container.batching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.spring.IdentifiableMessageListenerContainer;
import com.jashmore.sqs.spring.queue.QueueResolverService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.Method;

public class BatchingQueueWrapperTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ArgumentResolverService argumentResolverService;

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private QueueResolverService queueResolver;

    private BatchingQueueListenerWrapper batchingQueueListenerWrapper;

    @Before
    public void setUp() {
        batchingQueueListenerWrapper = new BatchingQueueListenerWrapper(argumentResolverService, sqsAsyncClient, queueResolver);
    }

    @Test
    public void queueListenerWrapperCanBuildMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        final Object bean = new BatchingQueueWrapperTest();
        final Method method = BatchingQueueWrapperTest.class.getMethod("myMethod");

        // act
        final IdentifiableMessageListenerContainer messageListenerContainer = batchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getContainer()).isInstanceOf(SimpleMessageListenerContainer.class);
    }

    @Test
    public void queueListenerWrapperWithoutIdentifierWillConstructOneByDefault() throws NoSuchMethodException {
        // arrange
        final Object bean = new BatchingQueueWrapperTest();
        final Method method = BatchingQueueWrapperTest.class.getMethod("myMethod");

        // act
        final IdentifiableMessageListenerContainer messageListenerContainer = batchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getIdentifier()).isEqualTo("com.jashmore.sqs.spring.container.batching.BatchingQueueWrapperTest#myMethod");
    }

    @Test
    public void queueListenerWrapperWithIdentifierWillUseThatForTheMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        final Object bean = new BatchingQueueWrapperTest();
        final Method method = BatchingQueueWrapperTest.class.getMethod("myMethodWithIdentifier");

        // act
        final IdentifiableMessageListenerContainer messageListenerContainer = batchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getIdentifier()).isEqualTo("identifier");
    }

    @Test
    public void queueIsResolvedViaTheQueueResolverService() throws NoSuchMethodException {
        // arrange
        final Object bean = new BatchingQueueWrapperTest();
        final Method method = BatchingQueueWrapperTest.class.getMethod("myMethod");

        // act
        batchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        verify(queueResolver).resolveQueueUrl("test");
    }


    @SuppressWarnings("WeakerAccess")
    @BatchingQueueListener("test")
    public void myMethod() {

    }

    @SuppressWarnings("WeakerAccess")
    @BatchingQueueListener(value = "test2", identifier = "identifier")
    public void myMethodWithIdentifier() {

    }
}