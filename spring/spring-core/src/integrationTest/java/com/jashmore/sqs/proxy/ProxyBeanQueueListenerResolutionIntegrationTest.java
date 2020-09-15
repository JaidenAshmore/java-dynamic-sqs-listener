package com.jashmore.sqs.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest(classes = { ProxyBeanQueueListenerResolutionIntegrationTest.TestConfig.class, QueueListenerConfiguration.class })
@ExtendWith(SpringExtension.class)
@Slf4j
class ProxyBeanQueueListenerResolutionIntegrationTest {
    private static final String QUEUE_NAME = "ProxyBeanQueueListenerResolutionIntegrationTest";

    private static final CountDownLatch proxiedTestMethodCompleted = new CountDownLatch(2);

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @SpringBootApplication
    @Configuration
    public static class TestConfig {

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @Aspect
        @Component
        public static class TestingAspect {

            @After("execution(* com.jashmore.sqs.proxy.ProxyBeanQueueListenerResolutionIntegrationTest.TestConfig.MessageListener.*(..))")
            public void wrapMethod() {
                proxiedTestMethodCompleted.countDown();
            }
        }

        @Service
        public static class MessageListener {

            @QueueListener(value = QUEUE_NAME)
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
