package com.jashmore.sqs.spring.container.batching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.container.SimpleMessageListenerContainer;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolverProperties;
import com.jashmore.sqs.resolver.batching.StaticBatchingMessageResolverProperties;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetrieverProperties;
import com.jashmore.sqs.retriever.batching.StaticBatchingMessageRetrieverProperties;
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
    public void invalidBatchSizeStringFailsToWrapMessageListener() throws Exception {
        // arrange
        when(environment.resolvePlaceholders(anyString())).thenReturn("1");
        when(environment.resolvePlaceholders("${prop.batchSize}")).thenReturn("Test Invalid");
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

    @Test
    public void batchingMessageRetrieversBuiltFromAnnotationProperties() throws Exception {
        // arrange
        final Method method = BatchingQueueListenerWrapperTest.class.getMethod("methodWithFields");
        final BatchingQueueListener annotation = method.getAnnotation(BatchingQueueListener.class);

        // act
        final BatchingMessageRetrieverProperties properties = batchingQueueListenerWrapper.batchingMessageRetrieverProperties(annotation);

        // assert
        assertThat(properties).isEqualTo(StaticBatchingMessageRetrieverProperties.builder()
                .visibilityTimeoutInSeconds(300)
                .messageRetrievalPollingPeriodInMs(40L)
                .numberOfThreadsWaitingTrigger(10)
                .build()
        );
    }

    @Test
    public void batchingMessageRetrieversBuiltFromAnnotationStringProperties() throws Exception {
        // arrange
        final Method method = BatchingQueueListenerWrapperTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        final BatchingQueueListener annotation = method.getAnnotation(BatchingQueueListener.class);
        when(environment.resolvePlaceholders("${prop.batchSize}")).thenReturn("8");
        when(environment.resolvePlaceholders("${prop.period}")).thenReturn("30");
        when(environment.resolvePlaceholders("${prop.visibility}")).thenReturn("40");

        // act
        final BatchingMessageRetrieverProperties properties = batchingQueueListenerWrapper.batchingMessageRetrieverProperties(annotation);

        // assert
        assertThat(properties).isEqualTo(StaticBatchingMessageRetrieverProperties.builder()
                .numberOfThreadsWaitingTrigger(8)
                .visibilityTimeoutInSeconds(40)
                .messageRetrievalPollingPeriodInMs(30L)
                .build()
        );
    }

    @Test
    public void batchingMessageResolverBuiltFromAnnotationProperties() throws Exception {
        // arrange
        final Method method = BatchingQueueListenerWrapperTest.class.getMethod("methodWithFields");
        final BatchingQueueListener annotation = method.getAnnotation(BatchingQueueListener.class);

        // act
        final BatchingMessageResolverProperties properties = batchingQueueListenerWrapper.batchingMessageResolverProperties(annotation);

        // assert
        assertThat(properties).isEqualTo(StaticBatchingMessageResolverProperties.builder()
                .bufferingSizeLimit(10)
                .bufferingTimeInMs(40L)
                .build()
        );
    }

    @Test
    public void batchingMessageResolverBuiltFromAnnotationStringProperties() throws Exception {
        // arrange
        final Method method = BatchingQueueListenerWrapperTest.class.getMethod("methodWithFieldsUsingEnvironmentProperties");
        final BatchingQueueListener annotation = method.getAnnotation(BatchingQueueListener.class);
        when(environment.resolvePlaceholders("${prop.batchSize}")).thenReturn("8");
        when(environment.resolvePlaceholders("${prop.period}")).thenReturn("30");
        when(environment.resolvePlaceholders("${prop.visibility}")).thenReturn("40");

        // act
        final BatchingMessageResolverProperties properties = batchingQueueListenerWrapper.batchingMessageResolverProperties(annotation);

        // assert
        assertThat(properties).isEqualTo(StaticBatchingMessageResolverProperties.builder()
                .bufferingSizeLimit(8)
                .bufferingTimeInMs(30L)
                .build()
        );
    }

    @BatchingQueueListener("test")
    public void myMethod() {

    }

    @BatchingQueueListener(value = "test2", identifier = "identifier")
    public void myMethodWithIdentifier() {

    }

    @BatchingQueueListener(value = "test2", concurrencyLevelString = "${prop.concurrency}", batchSizeString = "${prop.batchSize}",
            messageVisibilityTimeoutInSecondsString = "${prop.visibility}", maxPeriodBetweenBatchesInMsString = "${prop.period}")
    public void methodWithFieldsUsingEnvironmentProperties() {

    }

    @BatchingQueueListener(value = "test2", concurrencyLevel = 20, batchSize = 10, messageVisibilityTimeoutInSeconds = 300, maxPeriodBetweenBatchesInMs = 40)
    public void methodWithFields() {

    }
}