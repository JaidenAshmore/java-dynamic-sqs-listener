package com.jashmore.sqs.spring.container.prefetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.CoreMessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.retriever.prefetch.StaticPrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.container.MessageListenerContainerInitialisationException;
import com.jashmore.sqs.spring.queue.QueueResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Class is hard to test as it is the one building all of the dependencies internally using new constructors. Don't really know a better way to do this
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
    private Environment environment;

    private PrefetchingMessageListenerContainerFactory prefetchingQueueListenerWrapper;

    @BeforeEach
    void setUp() {
        prefetchingQueueListenerWrapper = new PrefetchingMessageListenerContainerFactory(argumentResolverService, sqsAsyncClientProvider, queueResolver, environment);
    }

    @Test
    void canBuildMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("myMethod");

        // act
        final MessageListenerContainer messageListenerContainer = prefetchingQueueListenerWrapper.buildContainer(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer).isInstanceOf(CoreMessageListenerContainer.class);
    }

    @Test
    void queueListenerWrapperWithoutIdentifierWillConstructOneByDefault() throws NoSuchMethodException {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("myMethod");

        // act
        final MessageListenerContainer messageListenerContainer = prefetchingQueueListenerWrapper.buildContainer(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getIdentifier())
                .isEqualTo("prefetching-message-listener-container-factory-test-my-method");
    }

    @Test
    void queueListenerWrapperWithIdentifierWillUseThatForTheMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("myMethodWithIdentifier");

        // act
        final MessageListenerContainer messageListenerContainer = prefetchingQueueListenerWrapper.buildContainer(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getIdentifier()).isEqualTo("identifier");
    }

    @Test
    void queueIsResolvedViaTheQueueResolver() throws NoSuchMethodException {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("myMethod");

        // act
        prefetchingQueueListenerWrapper.buildContainer(bean, method);

        // assert
        verify(queueResolver).resolveQueueUrl(defaultClient, "test");
    }

    @Test
    void invalidConcurrencyLevelStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.concurrency}")).thenReturn("Test Invalid");
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        assertThrows(NumberFormatException.class, () -> prefetchingQueueListenerWrapper.buildContainer(bean, method));
    }

    @Test
    void invalidMessageVisibilityTimeoutInSecondsStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.visibility}")).thenReturn("Test Invalid");
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        assertThrows(NumberFormatException.class, () -> prefetchingQueueListenerWrapper.buildContainer(bean, method));
    }

    @Test
    void invalidMaxPrefetchedMessagesStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.maxPrefetched}")).thenReturn("Test Invalid");
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        assertThrows(NumberFormatException.class, () -> prefetchingQueueListenerWrapper.buildContainer(bean, method));
    }

    @Test
    void invalidDesiredMinPrefetchedMessagesStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.desiredMinPrefetchedMessages}")).thenReturn("Test Invalid");
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        assertThrows(NumberFormatException.class, () -> prefetchingQueueListenerWrapper.buildContainer(bean, method));
    }

    @Test
    void validStringFieldsWillCorrectlyBuildMessageListener() throws Exception {
        // arrange
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        final MessageListenerContainer messageListenerContainer = prefetchingQueueListenerWrapper.buildContainer(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
    }

    @Test
    void prefetchingQueueListenerCanBeBuiltFromStringProperties() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        when(environment.resolvePlaceholders("${prop.maxPrefetched}")).thenReturn("30");
        when(environment.resolvePlaceholders("${prop.desiredMinPrefetchedMessages}")).thenReturn("40");
        when(environment.resolvePlaceholders("${prop.visibility}")).thenReturn("40");
        final PrefetchingQueueListener annotation = method.getAnnotation(PrefetchingQueueListener.class);

        // act
        final PrefetchingMessageRetrieverProperties properties = prefetchingQueueListenerWrapper.buildMessageRetrieverProperties(annotation);

        // assert
        assertThat(properties).isEqualTo(StaticPrefetchingMessageRetrieverProperties.builder()
                .maxPrefetchedMessages(30)
                .desiredMinPrefetchedMessages(40)
                .messageVisibilityTimeoutInSeconds(40)
                .build()
        );
    }

    @Test
    void prefetchingQueueListenerCanBeBuiltFromProperties() throws Exception {
        // arrange
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingProperties");
        final PrefetchingQueueListener annotation = method.getAnnotation(PrefetchingQueueListener.class);

        // act
        final PrefetchingMessageRetrieverProperties properties = prefetchingQueueListenerWrapper.buildMessageRetrieverProperties(annotation);

        // assert
        assertThat(properties).isEqualTo(StaticPrefetchingMessageRetrieverProperties.builder()
                .maxPrefetchedMessages(20)
                .desiredMinPrefetchedMessages(5)
                .messageVisibilityTimeoutInSeconds(300)
                .build()
        );
    }

    @Test
    void whenNoDefaultSqsClientAvailableAndItIsRequestedTheListenerWillNotBeWrapped() throws Exception {
        // arrange
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("myMethod");
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.empty());

        // act
        final MessageListenerContainerInitialisationException exception = assertThrows(MessageListenerContainerInitialisationException.class,
                () -> prefetchingQueueListenerWrapper.buildContainer(bean, method));

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
        final MessageListenerContainerInitialisationException exception = assertThrows(MessageListenerContainerInitialisationException.class,
                () -> prefetchingQueueListenerWrapper.buildContainer(bean, method));

        assertThat(exception).hasMessage("Expected a client with id 'clientId' but none were found");
    }

    @Test
    void whenSpecificSqsClientRequestWhichCanBeFoundTheContainerCanBeBuilt() throws Exception {
        // arrange
        final Object bean = new PrefetchingMessageListenerContainerFactoryTest();
        final Method method = PrefetchingMessageListenerContainerFactoryTest.class.getMethod("methodUsingSpecificSqsAsyncClient");
        when(sqsAsyncClientProvider.getClient("clientId")).thenReturn(Optional.of(mock(SqsAsyncClient.class)));

        // act
        final MessageListenerContainer container = prefetchingQueueListenerWrapper.buildContainer(bean, method);

        // assert
        assertThat(container).isNotNull();
    }

    @PrefetchingQueueListener("test")
    public void myMethod() {

    }

    @PrefetchingQueueListener(value = "test2", identifier = "identifier")
    public void myMethodWithIdentifier() {

    }

    @PrefetchingQueueListener(value = "test2", concurrencyLevelString = "${prop.concurrency}",
            messageVisibilityTimeoutInSecondsString = "${prop.visibility}", maxPrefetchedMessagesString = "${prop.maxPrefetched}",
            desiredMinPrefetchedMessagesString = "${prop.desiredMinPrefetchedMessages}"
    )
    public void methodWithFieldsUsingEnvironmentProperties() {

    }

    @PrefetchingQueueListener(value = "test2", concurrencyLevel = 2, messageVisibilityTimeoutInSeconds = 300,
            maxPrefetchedMessages = 20, desiredMinPrefetchedMessages = 5
    )
    public void methodWithFieldsUsingProperties() {

    }

    @PrefetchingQueueListener(value = "test2", sqsClient = "clientId")
    public void methodUsingSpecificSqsAsyncClient() {

    }
}
