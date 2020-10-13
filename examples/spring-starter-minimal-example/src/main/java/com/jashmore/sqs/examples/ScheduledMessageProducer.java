package com.jashmore.sqs.examples;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Component
@EnableScheduling
public class ScheduledMessageProducer {

    private final SqsAsyncClient sqsAsyncClient;

    @Autowired
    public ScheduledMessageProducer(SqsAsyncClient sqsAsyncClient) {
        this.sqsAsyncClient = sqsAsyncClient;
    }

    @Scheduled(fixedRate = 1_000)
    public void sendMessageToQueue() throws Exception {
        String queueUrl = sqsAsyncClient.getQueueUrl(builder -> builder.queueName("myQueueName")).get().queueUrl();
        sqsAsyncClient.sendMessage(builder -> builder.queueUrl(queueUrl).messageBody("hello world!"));
    }
}
