package com.jashmore.sqs.spring.container.basic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.batching.BatchingMessageListenerContainer;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.container.MessageListenerContainerInitialisationException;
import com.jashmore.sqs.spring.processor.DecoratingMessageProcessorFactory;
import com.jashmore.sqs.spring.queue.QueueResolver;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Class is hard to test as it is the one building all of the dependencies internally using new constructors. Don't really know a better way to do this
 * without building unnecessary classes.
 */
@SuppressWarnings("WeakerAccess")
@ExtendWith(MockitoExtension.class)
class BasicMessageListenerContainerFactoryTest {
    @Mock
    ArgumentResolverService argumentResolverService;

    @Mock
    SqsAsyncClientProvider sqsAsyncClientProvider;

    @Mock
    SqsAsyncClient defaultSqsAsyncClient;

    @Mock
    QueueResolver queueResolver;

    @Mock
    Environment environment;

    @Mock
    DecoratingMessageProcessorFactory decoratingMessageProcessorFactory;

    private BasicMessageListenerContainerFactory messageListenerContainerFactory;

    @BeforeEach
    void setUp() {
        messageListenerContainerFactory =
            new BasicMessageListenerContainerFactory(
                argumentResolverService,
                sqsAsyncClientProvider,
                queueResolver,
                environment,
                decoratingMessageProcessorFactory
            );
    }

    @Test
    void queueListenerWrapperCanBuildMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultSqsAsyncClient));
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("myMethod");

        // act
        final MessageListenerContainer messageListenerContainer = messageListenerContainerFactory.buildContainer(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer).isInstanceOf(BatchingMessageListenerContainer.class);
    }

    @Test
    void queueListenerWrapperWithoutIdentifierWillConstructOneByDefault() throws NoSuchMethodException {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultSqsAsyncClient));
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("myMethod");

        // act
        final MessageListenerContainer messageListenerContainer = messageListenerContainerFactory.buildContainer(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getIdentifier()).isEqualTo("basic-message-listener-container-factory-test-my-method");
    }

    @Test
    void queueListenerWrapperWithIdentifierWillUseThatForTheMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultSqsAsyncClient));
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("myMethodWithIdentifier");

        // act
        final MessageListenerContainer messageListenerContainer = messageListenerContainerFactory.buildContainer(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getIdentifier()).isEqualTo("identifier");
    }

    @Test
    void queueIsResolvedViaTheQueueResolver() throws NoSuchMethodException {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultSqsAsyncClient));
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("myMethod");

        // act
        messageListenerContainerFactory.buildContainer(bean, method);

        // assert
        verify(queueResolver).resolveQueueUrl(defaultSqsAsyncClient, "test");
    }

    @Test
    void invalidConcurrencyLevelStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultSqsAsyncClient));
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.concurrency}")).thenReturn("Test Invalid");
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        assertThrows(NumberFormatException.class, () -> messageListenerContainerFactory.buildContainer(bean, method));
    }

    @Test
    void invalidMessageVisibilityTimeoutInSecondsStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultSqsAsyncClient));
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.visibility}")).thenReturn("Test Invalid");
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        assertThrows(NumberFormatException.class, () -> messageListenerContainerFactory.buildContainer(bean, method));
    }

    @Test
    void invalidMaxPeriodBetweenBatchesInMsStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultSqsAsyncClient));
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.period}")).thenReturn("Test Invalid");
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        assertThrows(NumberFormatException.class, () -> messageListenerContainerFactory.buildContainer(bean, method));
    }

    @Test
    void validStringFieldsWillCorrectlyBuildMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultSqsAsyncClient));
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        final MessageListenerContainer messageListenerContainer = messageListenerContainerFactory.buildContainer(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
    }

    @Test
    void whenNoDefaultSqsClientAvailableAndItIsRequestedTheListenerWillNotBeWrapped() throws Exception {
        // arrange
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("myMethod");
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.empty());

        // act
        final MessageListenerContainerInitialisationException exception = assertThrows(
            MessageListenerContainerInitialisationException.class,
            () -> messageListenerContainerFactory.buildContainer(bean, method)
        );

        // assert
        assertThat(exception.getMessage()).isEqualTo("Expected the default SQS Client but there is none");
    }

    @Test
    void whenSpecificSqsClientRequestButNoneAvailableAnExceptionIsThrown() throws Exception {
        // arrange
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodUsingSpecificSqsAsyncClient");
        when(sqsAsyncClientProvider.getClient("clientId")).thenReturn(Optional.empty());

        // act
        final MessageListenerContainerInitialisationException exception = assertThrows(
            MessageListenerContainerInitialisationException.class,
            () -> messageListenerContainerFactory.buildContainer(bean, method)
        );

        // assert
        assertThat(exception.getMessage()).isEqualTo("Expected a client with id 'clientId' but none were found");
    }

    @Test
    void whenSpecificSqsClientRequestWhichCanBeFoundTheContainerCanBeBuilt() throws Exception {
        // arrange
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodUsingSpecificSqsAsyncClient");
        when(sqsAsyncClientProvider.getClient("clientId")).thenReturn(Optional.of(mock(SqsAsyncClient.class)));

        // act
        final MessageListenerContainer container = messageListenerContainerFactory.buildContainer(bean, method);

        // assert
        assertThat(container).isNotNull();
    }

    @QueueListener("test")
    public void myMethod() {}

    @QueueListener(value = "test2", identifier = "identifier")
    public void myMethodWithIdentifier() {}

    @QueueListener(
        value = "test2",
        concurrencyLevelString = "${prop.concurrency}",
        batchSizeString = "${prop.batchSize}",
        messageVisibilityTimeoutInSecondsString = "${prop.visibility}",
        batchingPeriodInMsString = "${prop.period}"
    )
    public void methodWithFieldsUsingEnvironmentProperties() {}

    @QueueListener(value = "test2", concurrencyLevel = 20, batchSize = 10, messageVisibilityTimeoutInSeconds = 300, batchingPeriodInMs = 40)
    public void methodWithFields() {}

    @QueueListener(value = "test2", sqsClient = "clientId")
    public void methodUsingSpecificSqsAsyncClient() {}
}
