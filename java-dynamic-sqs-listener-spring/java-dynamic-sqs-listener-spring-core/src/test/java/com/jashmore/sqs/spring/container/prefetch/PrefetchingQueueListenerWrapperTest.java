package com.jashmore.sqs.spring.container.prefetch;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.retriever.prefetch.StaticPrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.spring.IdentifiableMessageListenerContainer;
import com.jashmore.sqs.spring.QueueWrapperInitialisationException;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.queue.QueueResolverService;
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
public class PrefetchingQueueListenerWrapperTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private ArgumentResolverService argumentResolverService;

    @Mock
    private SqsAsyncClientProvider sqsAsyncClientProvider;

    @Mock
    private SqsAsyncClient defaultClient;

    @Mock
    private QueueResolverService queueResolver;

    @Mock
    private Environment environment;

    private PrefetchingQueueListenerWrapper prefetchingQueueListenerWrapper;

    @Before
    public void setUp() {
        prefetchingQueueListenerWrapper = new PrefetchingQueueListenerWrapper(argumentResolverService, sqsAsyncClientProvider, queueResolver, environment);

        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.of(defaultClient));
    }

    @Test
    public void canBuildMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("myMethod");

        // act
        final IdentifiableMessageListenerContainer messageListenerContainer = prefetchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getContainer()).isInstanceOf(SimpleMessageListenerContainer.class);
    }

    @Test
    public void queueListenerWrapperWithoutIdentifierWillConstructOneByDefault() throws NoSuchMethodException {
        // arrange
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("myMethod");

        // act
        final IdentifiableMessageListenerContainer messageListenerContainer = prefetchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getIdentifier())
                .isEqualTo("prefetching-queue-listener-wrapper-test-my-method");
    }

    @Test
    public void queueListenerWrapperWithIdentifierWillUseThatForTheMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("myMethodWithIdentifier");

        // act
        final IdentifiableMessageListenerContainer messageListenerContainer = prefetchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getIdentifier()).isEqualTo("identifier");
    }

    @Test
    public void queueIsResolvedViaTheQueueResolverService() throws NoSuchMethodException {
        // arrange
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("myMethod");

        // act
        prefetchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        verify(queueResolver).resolveQueueUrl(defaultClient, "test");
    }

    @Test
    public void invalidConcurrencyLevelStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.concurrency}")).thenReturn("Test Invalid");
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        expectedException.expect(NumberFormatException.class);

        // act
        prefetchingQueueListenerWrapper.wrapMethod(bean, method);
    }

    @Test
    public void invalidMessageVisibilityTimeoutInSecondsStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.visibility}")).thenReturn("Test Invalid");
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        expectedException.expect(NumberFormatException.class);

        // act
        prefetchingQueueListenerWrapper.wrapMethod(bean, method);
    }

    @Test
    public void invalidMaxPrefetchedMessagesStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.maxPrefetched}")).thenReturn("Test Invalid");
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        expectedException.expect(NumberFormatException.class);

        // act
        prefetchingQueueListenerWrapper.wrapMethod(bean, method);
    }

    @Test
    public void invalidDesiredMinPrefetchedMessagesStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.desiredMinPrefetchedMessages}")).thenReturn("Test Invalid");
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        expectedException.expect(NumberFormatException.class);

        // act
        prefetchingQueueListenerWrapper.wrapMethod(bean, method);
    }

    @Test
    public void validStringFieldsWillCorrectlyBuildMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        final IdentifiableMessageListenerContainer messageListenerContainer = prefetchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
    }

    @Test
    public void prefetchingQueueListenerCanBeBuiltFromStringProperties() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
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
                .visibilityTimeoutForMessagesInSeconds(40)
                .maxWaitTimeInSecondsToObtainMessagesFromServer(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
                .build()
        );
    }

    @Test
    public void prefetchingQueueListenerCanBeBuiltFromProperties() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("methodWithFieldsUsingProperties");
        final PrefetchingQueueListener annotation = method.getAnnotation(PrefetchingQueueListener.class);

        // act
        final PrefetchingMessageRetrieverProperties properties = prefetchingQueueListenerWrapper.buildMessageRetrieverProperties(annotation);

        // assert
        assertThat(properties).isEqualTo(StaticPrefetchingMessageRetrieverProperties.builder()
                .maxPrefetchedMessages(20)
                .desiredMinPrefetchedMessages(5)
                .visibilityTimeoutForMessagesInSeconds(300)
                .maxWaitTimeInSecondsToObtainMessagesFromServer(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS)
                .build()
        );
    }

    @Test
    public void whenNoDefaultSqsClientAvailableAndItIsRequestedTheListenerWillNotBeWrapped() throws Exception {
        // arrange
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("myMethod");
        when(sqsAsyncClientProvider.getDefaultClient()).thenReturn(Optional.empty());
        expectedException.expect(QueueWrapperInitialisationException.class);
        expectedException.expectMessage("Expected the default SQS Client but there is none");

        // act
        prefetchingQueueListenerWrapper.wrapMethod(bean, method);
    }

    @Test
    public void whenSpecificSqsClientRequestButNoneAvailableAnExceptionIsThrown() throws Exception {
        // arrange
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("methodUsingSpecificSqsAsyncClient");
        when(sqsAsyncClientProvider.getClient("clientId")).thenReturn(Optional.empty());
        expectedException.expect(QueueWrapperInitialisationException.class);
        expectedException.expectMessage("Expected a client with id 'clientId' but none were found");

        // act
        prefetchingQueueListenerWrapper.wrapMethod(bean, method);
    }

    @Test
    public void whenSpecificSqsClientRequestWhichCanBeFoundTheContainerCanBeBuilt() throws Exception {
        // arrange
        final Object bean = new PrefetchingQueueListenerWrapperTest();
        final Method method = PrefetchingQueueListenerWrapperTest.class.getMethod("methodUsingSpecificSqsAsyncClient");
        when(sqsAsyncClientProvider.getClient("clientId")).thenReturn(Optional.of(mock(SqsAsyncClient.class)));

        // act
        final IdentifiableMessageListenerContainer container = prefetchingQueueListenerWrapper.wrapMethod(bean, method);

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
