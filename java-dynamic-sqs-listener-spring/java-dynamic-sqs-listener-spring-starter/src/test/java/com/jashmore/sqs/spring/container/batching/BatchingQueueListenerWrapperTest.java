package com.jashmore.sqs.spring.container.batching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.spring.IdentifiableMessageListenerContainer;
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

@SuppressWarnings("WeakerAccess")
public class BatchingQueueListenerWrapperTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private ArgumentResolverService argumentResolverService;

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private QueueResolverService queueResolver;

    @Mock
    private Environment environment;

    private BatchingQueueListenerWrapper batchingQueueListenerWrapper;

    @Before
    public void setUp() {
        batchingQueueListenerWrapper = new BatchingQueueListenerWrapper(argumentResolverService, sqsAsyncClient, queueResolver, environment);
    }

    @Test
    public void queueListenerWrapperCanBuildMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        final Object bean = new BatchingQueueListenerWrapperTest();
        final Method method = BatchingQueueListenerWrapperTest.class.getMethod("myMethod");

        // act
        final IdentifiableMessageListenerContainer messageListenerContainer = batchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getContainer()).isInstanceOf(SimpleMessageListenerContainer.class);
    }

    @Test
    public void queueListenerWrapperWithoutIdentifierWillConstructOneByDefault() throws NoSuchMethodException {
        // arrange
        final Object bean = new BatchingQueueListenerWrapperTest();
        final Method method = BatchingQueueListenerWrapperTest.class.getMethod("myMethod");

        // act
        final IdentifiableMessageListenerContainer messageListenerContainer = batchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getIdentifier()).isEqualTo("batching-queue-listener-wrapper-test-my-method");
    }

    @Test
    public void queueListenerWrapperWithIdentifierWillUseThatForTheMessageListenerContainer() throws NoSuchMethodException {
        // arrange
        final Object bean = new BatchingQueueListenerWrapperTest();
        final Method method = BatchingQueueListenerWrapperTest.class.getMethod("myMethodWithIdentifier");

        // act
        final IdentifiableMessageListenerContainer messageListenerContainer = batchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
        assertThat(messageListenerContainer.getIdentifier()).isEqualTo("identifier");
    }

    @Test
    public void queueIsResolvedViaTheQueueResolverService() throws NoSuchMethodException {
        // arrange
        final Object bean = new BatchingQueueListenerWrapperTest();
        final Method method = BatchingQueueListenerWrapperTest.class.getMethod("myMethod");

        // act
        batchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        verify(queueResolver).resolveQueueUrl("test");
    }

    @Test
    public void invalidConcurrencyLevelStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.concurrency}")).thenReturn("Test Invalid");
        final Object bean = new BatchingQueueListenerWrapperTest();
        final Method method = BatchingQueueListenerWrapperTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        expectedException.expect(NumberFormatException.class);

        // act
        batchingQueueListenerWrapper.wrapMethod(bean, method);
    }

    @Test
    public void invalidMessageVisibilityTimeoutInSecondsStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.visibility}")).thenReturn("Test Invalid");
        final Object bean = new BatchingQueueListenerWrapperTest();
        final Method method = BatchingQueueListenerWrapperTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        expectedException.expect(NumberFormatException.class);

        // act
        batchingQueueListenerWrapper.wrapMethod(bean, method);
    }

    @Test
    public void invalidMaxPeriodBetweenBatchesInMsStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.period}")).thenReturn("Test Invalid");
        final Object bean = new BatchingQueueListenerWrapperTest();
        final Method method = BatchingQueueListenerWrapperTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        expectedException.expect(NumberFormatException.class);

        // act
        batchingQueueListenerWrapper.wrapMethod(bean, method);
    }

    @Test
    public void validStringFieldsWillCorrectlyBuildMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        final Object bean = new BatchingQueueListenerWrapperTest();
        final Method method = BatchingQueueListenerWrapperTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");

        // act
        final IdentifiableMessageListenerContainer messageListenerContainer = batchingQueueListenerWrapper.wrapMethod(bean, method);

        // assert
        assertThat(messageListenerContainer).isNotNull();
    }

    @BatchingQueueListener("test")
    public void myMethod() {

    }

    @BatchingQueueListener(value = "test2", identifier = "identifier")
    public void myMethodWithIdentifier() {

    }

    @BatchingQueueListener(value = "test2", concurrencyLevelString = "${prop.concurrency}",
            messageVisibilityTimeoutInSecondsString = "${prop.visibility}", maxPeriodBetweenBatchesInMsString = "${prop.period}")
    public void methodWithFieldsUsingEnvironmentProperties() {

    }
}