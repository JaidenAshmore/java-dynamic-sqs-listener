package com.jashmore.sqs.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import io.micronaut.aop.Around;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@MicronautTest(environments = "ProxyBeanQueueListenerResolutionIntegrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class MicronautProxyBeanQueueListenerResolutionIntegrationTest {

    private static final String QUEUE_NAME = "ProxyBeanQueueListenerResolutionIntegrationTest";

    private static final CountDownLatch proxiedTestMethodCompleted = new CountDownLatch(2);

    @Inject
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Factory
    @Requires(env = "ProxyBeanQueueListenerResolutionIntegrationTest")
    public static class TestConfig {

        @Singleton
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.METHOD)
        @Around
        @Type(TestingAspect.class)
        @interface TestingInterceptor {
        }

        @Singleton
        public static class TestingAspect implements MethodInterceptor<MessageListener, Object> {

            @Override
            public @Nullable Object intercept(MethodInvocationContext<MessageListener, Object> context) {
                try {
                    return context.proceed();
                } finally {
                    proxiedTestMethodCompleted.countDown();
                }
            }
        }

        @Singleton
        @Requires(env = "ProxyBeanQueueListenerResolutionIntegrationTest")
        public static class MessageListener {

            @QueueListener(value = QUEUE_NAME)
            @TestingInterceptor
            public void listenToMessage(@Payload final String payload) {
                log.info("Message received: {}", payload);
                proxiedTestMethodCompleted.countDown();
            }
        }
    }

    @Test
    void classesThatAreProxiedShouldBeAbleToListenToMessagesWhenMethodsAndParametersAreAnnotated() throws Exception {
        // act
        localSqsAsyncClient.sendMessage(QUEUE_NAME, "message");

        // assert
        assertThat(proxiedTestMethodCompleted.await(1, TimeUnit.SECONDS)).isTrue();
    }
}
