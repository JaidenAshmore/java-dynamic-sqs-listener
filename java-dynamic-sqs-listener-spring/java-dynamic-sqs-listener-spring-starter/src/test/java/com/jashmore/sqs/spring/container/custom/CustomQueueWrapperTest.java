package com.jashmore.sqs.spring.container.custom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.spring.container.MessageListenerContainer;
import com.jashmore.sqs.spring.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.spring.queue.QueueResolverService;
import com.jashmore.sqs.retriever.MessageRetriever;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Method;

public class CustomQueueWrapperTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BeanFactory beanFactory;

    @Mock
    private QueueResolverService queueResolver;

    @Mock
    private MessageRetrieverFactory messageRetrieverFactory;

    @Mock
    private MessageProcessorFactory messageProcessorFactory;

    @Mock
    private MessageBrokerFactory messageBrokerFactory;

    private CustomQueueWrapper customQueueWrapper;

    @Before
    public void setUp() {
        customQueueWrapper = new CustomQueueWrapper(beanFactory, queueResolver);
    }

    @Test
    public void factoriesAreCalledToBuildBeans() throws NoSuchMethodException, InterruptedException {
        // arrange
        when(beanFactory.getBean("retriever", MessageRetrieverFactory.class)).thenReturn(messageRetrieverFactory);
        when(beanFactory.getBean("processor", MessageProcessorFactory.class)).thenReturn(messageProcessorFactory);
        when(beanFactory.getBean("broker", MessageBrokerFactory.class)).thenReturn(messageBrokerFactory);
        final Object bean = new CustomQueueWrapperTest();
        final Method method = CustomQueueWrapperTest.class.getMethod("myMethod");

        // act
        customQueueWrapper.wrapMethod(bean, method);

        // assert
        verify(queueResolver).resolveQueueUrl("test");
    }

    @Test
    public void messageListenerContainerIsCorrectlyBuiltForWrappedMethod() throws NoSuchMethodException {
        // arrange
        when(beanFactory.getBean("retriever", MessageRetrieverFactory.class)).thenReturn(messageRetrieverFactory);
        when(beanFactory.getBean("processor", MessageProcessorFactory.class)).thenReturn(messageProcessorFactory);
        when(beanFactory.getBean("broker", MessageBrokerFactory.class)).thenReturn(messageBrokerFactory);
        final Object bean = new CustomQueueWrapperTest();
        final Method method = CustomQueueWrapperTest.class.getMethod("myMethod");

        // act
        final MessageListenerContainer messageListenerContainer = customQueueWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer).isInstanceOf(SimpleMessageListenerContainer.class);
    }

    @Test
    public void queueIsResolvedViaTheQueueResolverService() throws NoSuchMethodException {
        // arrange
        final Object bean = new CustomQueueWrapperTest();
        final Method method = CustomQueueWrapperTest.class.getMethod("myMethod");
        final MessageRetriever messageRetriever = mock(MessageRetriever.class);
        final MessageProcessor messageProcessor = mock(MessageProcessor.class);
        final MessageBroker messageBroker = mock(MessageBroker.class);
        when(messageRetrieverFactory.createMessageRetriever(any(QueueProperties.class))).thenReturn(messageRetriever);
        when(messageProcessorFactory.createMessageProcessor(any(QueueProperties.class), eq(bean), eq(method))).thenReturn(messageProcessor);
        when(messageBrokerFactory.createMessageBroker(messageRetriever, messageProcessor)).thenReturn(messageBroker);
        when(beanFactory.getBean("retriever", MessageRetrieverFactory.class)).thenReturn(messageRetrieverFactory);
        when(beanFactory.getBean("processor", MessageProcessorFactory.class)).thenReturn(messageProcessorFactory);
        when(beanFactory.getBean("broker", MessageBrokerFactory.class)).thenReturn(messageBrokerFactory);

        // act
        customQueueWrapper.wrapMethod(bean, method);

        // assert
        verify(messageRetrieverFactory).createMessageRetriever(any(QueueProperties.class));
        verify(messageProcessorFactory).createMessageProcessor(any(QueueProperties.class), eq(bean), eq(method));
        verify(messageBrokerFactory).createMessageBroker(messageRetriever, messageProcessor);
    }

    @SuppressWarnings("WeakerAccess")
    @CustomQueueListener(
            queue = "test",
            messageRetrieverFactoryBeanName = "retriever",
            messageProcessorFactoryBeanName = "processor",
            messageBrokerFactoryBeanName = "broker"
    )
    public void myMethod() {

    }
}
