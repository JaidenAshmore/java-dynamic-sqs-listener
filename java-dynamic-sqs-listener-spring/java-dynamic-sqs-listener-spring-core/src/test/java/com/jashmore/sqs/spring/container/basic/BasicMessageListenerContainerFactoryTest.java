package com.jashmore.sqs.spring.container.basic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties;
import com.jashmore.sqs.retriever.batching.StaticBatchingMessageRetrieverProperties;
import com.jashmore.sqs.spring.container.MessageListenerContainerInitialisationException;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.queue.QueueResolver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Class is hard to test as it is the one building all of the dependencies internally using new constructors. Don't really know a better way to do this
 * without building unnecessary classes.
 */
@SuppressWarnings("WeakerAccess")
public class BasicMessageListenerContainerFactoryTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private ArgumentResolverService argumentResolverService;

    @Mock
    private SqsAsyncClientProvider sqsAsyncClientProvider;

    @Mock
    private SqsAsyncClient defaultSqsAsyncClient;

    @Mock
    private QueueResolver queueResolver;

    @Mock
    private Environment environment;

    private BasicMessageListenerContainerFactory queueListenerWrapper;

    @Before
    public void setUp() {
        queueListenerWrapper = new BasicMessageListenerContainerFactory(argumentResolverService, sqsAsyncClientProvider, queueResolver, environment);

        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultSqsAsyncClient));
    }

    @Test
    public void queueListenerWrapperCanBuildMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("myMethod");

        // act
        final MessageListenerContainer messageListenerContainer = queueListenerWrapper.buildContainer(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer).isInstanceOf(SimpleMessageListenerContainer.class);
    }

    @Test
    public void queueListenerWrapperWithoutIdentifierWillConstructOneByDefault() throws NoSuchMethodException {
        // arrange
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("myMethod");

        // act
        final MessageListenerContainer messageListenerContainer = queueListenerWrapper.buildContainer(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getIdentifier()).isEqualTo("basic-message-listener-container-factory-test-my-method");
    }

    @Test
    public void queueListenerWrapperWithIdentifierWillUseThatForTheMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("myMethodWithIdentifier");

        // act
        final MessageListenerContainer messageListenerContainer = queueListenerWrapper.buildContainer(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getIdentifier()).isEqualTo("identifier");
    }

    @Test
    public void queueIsResolvedViaTheQueueResolver() throws NoSuchMethodException {
        // arrange
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("myMethod");

        // act
        queueListenerWrapper.buildContainer(bean, method);

        // assert
        verify(queueResolver).resolveQueueUrl(defaultSqsAsyncClient, "test");
    }

    @Test
    public void invalidConcurrencyLevelStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.concurrency}")).thenReturn("Test Invalid");
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        expectedException.expect(NumberFormatException.class);

        // act
        queueListenerWrapper.buildContainer(bean, method);
    }

    @Test
    public void invalidMessageVisibilityTimeoutInSecondsStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.visibility}")).thenReturn("Test Invalid");
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        expectedException.expect(NumberFormatException.class);

        // act
        queueListenerWrapper.buildContainer(bean, method);
    }

    @Test
    public void invalidMaxPeriodBetweenBatchesInMsStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.period}")).thenReturn("Test Invalid");
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        expectedException.expect(NumberFormatException.class);

        // act
        queueListenerWrapper.buildContainer(bean, method);
    }

    @Test
    public void validStringFieldsWillCorrectlyBuildMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        final MessageListenerContainer messageListenerContainer = queueListenerWrapper.buildContainer(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
    }

    @Test
    public void batchingMessageRetrieverPropertiesBuiltFromAnnotationValues() throws Exception {
        // arrange
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodWithFields");
        final QueueListener annotation = method.getAnnotation(QueueListener.class);

        // act
        final BatchingMessageRetrieverProperties properties
                = queueListenerWrapper.batchingMessageRetrieverProperties(annotation);

        // assert
        assertThat(properties).isEqualTo(StaticBatchingMessageRetrieverProperties.builder()
                .messageVisibilityTimeoutInSeconds(300)
                .messageRetrievalPollingPeriodInMs(40L)
                .numberOfThreadsWaitingTrigger(10)
                .build()
        );
    }

    @Test
    public void batchingMessageRetrieverPropertiesBuiltFromSpringValues() throws Exception {
        // arrange
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        final QueueListener annotation = method.getAnnotation(QueueListener.class);
        when(environment.resolvePlaceholders("${prop.batchSize}")).thenReturn("8");
        when(environment.resolvePlaceholders("${prop.period}")).thenReturn("30");
        when(environment.resolvePlaceholders("${prop.visibility}")).thenReturn("40");

        // act
        final BatchingMessageRetrieverProperties properties
                = queueListenerWrapper.batchingMessageRetrieverProperties(annotation);

        // assert
        assertThat(properties).isEqualTo(StaticBatchingMessageRetrieverProperties.builder()
                .messageVisibilityTimeoutInSeconds(40)
                .messageRetrievalPollingPeriodInMs(30L)
                .numberOfThreadsWaitingTrigger(8)
                .build()
        );
    }

    @Test
    public void whenNoDefaultSqsClientAvailableAndItIsRequestedTheListenerWillNotBeWrapped() throws Exception {
        // arrange
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("myMethod");
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.empty());
        expectedException.expect(MessageListenerContainerInitialisationException.class);
        expectedException.expectMessage("Expected the default SQS Client but there is none");

        // act
        queueListenerWrapper.buildContainer(bean, method);
    }

    @Test
    public void whenSpecificSqsClientRequestButNoneAvailableAnExceptionIsThrown() throws Exception {
        // arrange
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodUsingSpecificSqsAsyncClient");
        when(sqsAsyncClientProvider.getClient("clientId")).thenReturn(Optional.empty());
        expectedException.expect(MessageListenerContainerInitialisationException.class);
        expectedException.expectMessage("Expected a client with id 'clientId' but none were found");

        // act
        queueListenerWrapper.buildContainer(bean, method);
    }

    @Test
    public void whenSpecificSqsClientRequestWhichCanBeFoundTheContainerCanBeBuilt() throws Exception {
        // arrange
        final Object bean = new BasicMessageListenerContainerFactoryTest();
        final Method method = BasicMessageListenerContainerFactoryTest.class.getMethod("methodUsingSpecificSqsAsyncClient");
        when(sqsAsyncClientProvider.getClient("clientId")).thenReturn(Optional.of(mock(SqsAsyncClient.class)));

        // act
        final MessageListenerContainer container = queueListenerWrapper.buildContainer(bean, method);

        // assert
        assertThat(container).isNotNull();
    }

    @QueueListener("test")
    public void myMethod() {

    }

    @QueueListener(value = "test2", identifier = "identifier")
    public void myMethodWithIdentifier() {

    }

    @QueueListener(value = "test2", concurrencyLevelString = "${prop.concurrency}", batchSizeString = "${prop.batchSize}",
            messageVisibilityTimeoutInSecondsString = "${prop.visibility}", maxPeriodBetweenBatchesInMsString = "${prop.period}")
    public void methodWithFieldsUsingEnvironmentProperties() {

    }

    @QueueListener(value = "test2", concurrencyLevel = 20, batchSize = 10, messageVisibilityTimeoutInSeconds = 300, maxPeriodBetweenBatchesInMs = 40)
    public void methodWithFields() {

    }

    @QueueListener(value = "test2", sqsClient = "clientId")
    public void methodUsingSpecificSqsAsyncClient() {

    }
}
