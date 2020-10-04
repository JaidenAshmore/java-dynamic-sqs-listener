package com.jashmore.sqs.examples;

import static com.jashmore.sqs.examples.Application.QUEUE_NAME;

import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Slf4j
@Component
public class ScheduledMessageProducer {
    private final SqsAsyncClient sqsAsyncClient;
    private final String queueUrl;

    @Autowired
    private ScheduledMessageProducer(final SqsAsyncClient sqsAsyncClient) {
        this.sqsAsyncClient = sqsAsyncClient;

        try {
            this.queueUrl = sqsAsyncClient.getQueueUrl(request -> request.queueName(QUEUE_NAME)).get().queueUrl();
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error determining queue URL");
        } catch (final ExecutionException executionException) {
            throw new RuntimeException(executionException);
        }
    }

    @Scheduled(initialDelay = 1_000, fixedDelay = 100)
    public void addMessages() {
        sqsAsyncClient.sendMessage(builder -> builder.queueUrl(queueUrl).messageBody("body"));
    }
}
