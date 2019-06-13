package it.com.jashmore.sqs.argument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.jashmore.sqs.argument.attribute.MessageAttribute;
import com.jashmore.sqs.argument.attribute.MessageAttributeDataTypes;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.test.LocalSqsRule;
import com.jashmore.sqs.test.PurgeQueuesRule;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import it.com.jashmore.example.Application;
import lombok.extern.slf4j.Slf4j;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit4.SpringRunner;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unused")
@Slf4j
@SpringBootTest(classes = {Application.class, MessageAttributeSpringIntegrationTest.TestConfig.class}, webEnvironment = RANDOM_PORT)
@RunWith(SpringRunner.class)
public class MessageAttributeSpringIntegrationTest {
    private static final String QUEUE_NAME = "MessageAttributeSpringIntegrationTest";
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(1);
    private static final AtomicReference<String> messageAttributeReference = new AtomicReference<>();

    @ClassRule
    public static final LocalSqsRule LOCAL_SQS_RULE = new LocalSqsRule(ImmutableList.of(
            SqsQueuesConfig.QueueConfig.builder().queueName(QUEUE_NAME).build()
    ));

    @Rule
    public final PurgeQueuesRule purgeQueuesRule = new PurgeQueuesRule(LOCAL_SQS_RULE.getLocalAmazonSqsAsync());

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Configuration
    public static class TestConfig {
        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return LOCAL_SQS_RULE.getLocalAmazonSqsAsync();
        }

        @Service
        public static class MessageListener {
            @QueueListener(value = QUEUE_NAME)
            public void listenToMessage(@MessageAttribute("key") final String value) {
                log.info("Obtained message: {}", value);
                messageAttributeReference.set(value);
                COUNT_DOWN_LATCH.countDown();
            }
        }
    }

    @Test
    public void allMessagesAreProcessedByListeners() throws Exception {
        // arrange
        localSqsAsyncClient.sendMessageToLocalQueue(QUEUE_NAME, SendMessageRequest.builder()
                .messageBody("message")
                .messageAttributes(ImmutableMap.of(
                        "key", MessageAttributeValue.builder()
                                .dataType(MessageAttributeDataTypes.STRING.getValue())
                                .stringValue("value")
                                .build()
                ))
                .build())
                .get(5, TimeUnit.SECONDS);

        // act
        COUNT_DOWN_LATCH.await(20, TimeUnit.SECONDS);

        // assert
        assertThat(messageAttributeReference).hasValue("value");
    }
}
