package com.jashmore.sqs.annotations.core.prefetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.client.QueueResolver;
import com.jashmore.sqs.client.SqsAsyncClientProvider;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainerInitialisationException;
import com.jashmore.sqs.container.prefetching.PrefetchingMessageListenerContainer;
import com.jashmore.sqs.placeholder.PlaceholderResolver;
import com.jashmore.sqs.processor.DecoratingMessageProcessorFactory;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Class is hard to test as it is the one building all the dependencies internally using new constructors. Don't really know a better way to do this
 * without building unnecessary classes.
 */
@SuppressWarnings("WeakerAccess")
@ExtendWith(MockitoExtension.class)
class PrefetchingMessageListenerContainerFactoryTest {

    @Mock
    private ArgumentResolverService argumentResolverService;

    @Mock
    private SqsAsyncClientProvider sqsAsyncClientProvider;

    @Mock
    private SqsAsyncClient defaultClient;

    @Mock
    private QueueResolver queueResolver;

    @Mock
    private PlaceholderResolver placeholderResolver;

    @Mock
    DecoratingMessageProcessorFactory decoratingMessageProcessorFactory;

    private PrefetchingAnnotationMessageListenerContainerFactory prefetchingMessageListenerContainerFactory;

    @BeforeEach
    void setUp() {
        prefetchingMessageListenerContainerFactory =
            new PrefetchingAnnotationMessageListenerContainerFactory(
                argumentResolverService,
                sqsAsyncClientProvider,
                queueResolver,
                new PrefetchingQueueListenerParser(placeholderResolver),
                decoratingMessageProcessorFactory
            );
    }

    @Test
    void canBuildMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("myMethod");

        // act
        final Optional<MessageListenerContainer> messageListenerContainer = prefetchingMessageListenerContainerFactory.buildContainer(
            bean,
            method
        );

        // assert
        assertThat(messageListenerContainer).containsInstanceOf(PrefetchingMessageListenerContainer.class);
    }

    @Test
    void queueListenerWrapperWithoutIdentifierWillConstructOneByDefault() throws NoSuchMethodException {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("myMethod");

        // act
        final Optional<MessageListenerContainer> messageListenerContainer = prefetchingMessageListenerContainerFactory.buildContainer(
            bean,
            method
        );

        // assert
        assertThat(messageListenerContainer).isNotEmpty();
        assertThat(messageListenerContainer.get().getIdentifier())
            .isEqualTo("prefetching-message-listener-container-factory-test-my-method");
    }

    @Test
    void queueListenerWrapperWithIdentifierWillUseThatForTheMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("myMethodWithIdentifier");

        // act
        final Optional<MessageListenerContainer> messageListenerContainer = prefetchingMessageListenerContainerFactory.buildContainer(
            bean,
            method
        );

        // assert
        assertThat(messageListenerContainer).isNotEmpty();
        assertThat(messageListenerContainer.get().getIdentifier()).isEqualTo("identifier");
    }

    @Test
    void queueIsResolvedViaTheQueueResolver() throws NoSuchMethodException {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("myMethod");

        // act
        prefetchingMessageListenerContainerFactory.buildContainer(bean, method);

        // assert
        verify(queueResolver).resolveQueueUrl(defaultClient, "test");
    }

    @Test
    void invalidConcurrencyLevelStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        when(placeholderResolver.resolvePlaceholders(anyString())).thenReturn("1");
        when(placeholderResolver.resolvePlaceholders("${prop.concurrency}")).thenReturn("Test Invalid");
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        assertThrows(NumberFormatException.class, () -> prefetchingMessageListenerContainerFactory.buildContainer(bean, method));
    }

    @Test
    void invalidMessageVisibilityTimeoutInSecondsStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        when(placeholderResolver.resolvePlaceholders(anyString())).thenReturn("1");
        when(placeholderResolver.resolvePlaceholders("${prop.visibility}")).thenReturn("Test Invalid");
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        assertThrows(NumberFormatException.class, () -> prefetchingMessageListenerContainerFactory.buildContainer(bean, method));
    }

    @Test
    void invalidMaxPrefetchedMessagesStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        when(placeholderResolver.resolvePlaceholders(anyString())).thenReturn("1");
        when(placeholderResolver.resolvePlaceholders("${prop.maxPrefetched}")).thenReturn("Test Invalid");
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        assertThrows(NumberFormatException.class, () -> prefetchingMessageListenerContainerFactory.buildContainer(bean, method));
    }

    @Test
    void invalidDesiredMinPrefetchedMessagesStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        when(placeholderResolver.resolvePlaceholders(anyString())).thenReturn("1");
        when(placeholderResolver.resolvePlaceholders("${prop.desiredMinPrefetchedMessages}")).thenReturn("Test Invalid");
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        assertThrows(NumberFormatException.class, () -> prefetchingMessageListenerContainerFactory.buildContainer(bean, method));
    }

    @Test
    void validStringFieldsWillCorrectlyBuildMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        when(placeholderResolver.resolvePlaceholders(anyString())).thenReturn("1");
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        final Optional<MessageListenerContainer> messageListenerContainer = prefetchingMessageListenerContainerFactory.buildContainer(
            bean,
            method
        );

        // assert
        assertThat(messageListenerContainer).isNotEmpty();
    }

    @Test
    void whenNoDefaultSqsClientAvailableAndItIsRequestedTheListenerWillNotBeWrapped() throws Exception {
        // arrange
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("myMethod");
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.empty());

        // act
        final MessageListenerContainerInitialisationException exception = assertThrows(
            MessageListenerContainerInitialisationException.class,
            () -> prefetchingMessageListenerContainerFactory.buildContainer(bean, method)
        );

        // assert
        assertThat(exception).hasMessage("Expected the default SQS Client but there is none");
    }

    @Test
    void whenSpecificSqsClientRequestButNoneAvailableAnExceptionIsThrown() throws Exception {
        // arrange
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodUsingSpecificSqsAsyncClient");
        when(sqsAsyncClientProvider.getClient("clientId")).thenReturn(Optional.empty());

        // act
        final MessageListenerContainerInitialisationException exception = assertThrows(
            MessageListenerContainerInitialisationException.class,
            () -> prefetchingMessageListenerContainerFactory.buildContainer(bean, method)
        );

        assertThat(exception).hasMessage("Expected a client with id 'clientId' but none were found");
    }

    @Test
    void whenSpecificSqsClientRequestWhichCanBeFoundTheContainerCanBeBuilt() throws Exception {
        // arrange
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodUsingSpecificSqsAsyncClient");
        when(sqsAsyncClientProvider.getClient("clientId")).thenReturn(Optional.of(mock(SqsAsyncClient.class)));

        // act
        final Optional<MessageListenerContainer> container = prefetchingMessageListenerContainerFactory.buildContainer(bean, method);

        // assert
        assertThat(container).isNotEmpty();
    }

    @PrefetchingQueueListener("test")
    public void myMethod() {}

    @PrefetchingQueueListener(value = "test2", identifier = "identifier")
    public void myMethodWithIdentifier() {}

    @PrefetchingQueueListener(
        value = "test2",
        concurrencyLevelString = "${prop.concurrency}",
        messageVisibilityTimeoutInSecondsString = "${prop.visibility}",
        maxPrefetchedMessagesString = "${prop.maxPrefetched}",
        desiredMinPrefetchedMessagesString = "${prop.desiredMinPrefetchedMessages}"
    )
    public void methodWithFieldsUsingEnvironmentProperties() {}

    @PrefetchingQueueListener(
        value = "test2",
        concurrencyLevel = 2,
        messageVisibilityTimeoutInSeconds = 300,
        maxPrefetchedMessages = 20,
        desiredMinPrefetchedMessages = 5
    )
    public void methodWithFieldsUsingProperties() {}

    @PrefetchingQueueListener(value = "test2", sqsClient = "clientId")
    public void methodUsingSpecificSqsAsyncClient() {}
}
