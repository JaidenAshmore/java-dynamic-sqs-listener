package it.com.jashmore.sqs.proxy;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.test.LocalSqsRule;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import it.com.jashmore.example.Application;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SpringBootTest(classes = {Application.class, ProxyBeanQueueListenerResolutionIntegrationTest.TestConfig.class}, webEnvironment = RANDOM_PORT)
@RunWith(SpringRunner.class)
@Slf4j
public class ProxyBeanQueueListenerResolutionIntegrationTest {
    private static final String QUEUE_NAME = "ProxyBeanQueueListenerResolutionIntegrationTest";

    @ClassRule
    public static final LocalSqsRule LOCAL_SQS_RULE = new LocalSqsRule(ImmutableList.of(
            SqsQueuesConfig.QueueConfig.builder().queueName(QUEUE_NAME).build()
    ));

    private static final CountDownLatch proxiedTestMethodCompleted = new CountDownLatch(2);

    @Configuration
    public static class TestConfig {
        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return LOCAL_SQS_RULE.getLocalAmazonSqsAsync();
        }

        @Aspect
        @Component
        public static class TestingAspect {
            @After("execution(* it.com.jashmore.sqs.proxy.ProxyBeanQueueListenerResolutionIntegrationTest.TestConfig.MessageListener.*(..))")
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

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Test
    public void classesThatAreProxiedShouldBeAbleToListenToMessagesWhenMethodsAndParametersAreAnnotated() throws Exception {
        // act
        localSqsAsyncClient.sendMessageToLocalQueue(QUEUE_NAME, "message");

        // assert
        assertThat(proxiedTestMethodCompleted.await(1, TimeUnit.SECONDS)).isTrue();
    }
}
