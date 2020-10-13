package com.jashmore.sqs.examples.sleuth;

import brave.ScopedSpan;
import brave.Tracing;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;

@Slf4j
@Component
@EnableScheduling
@AllArgsConstructor
public class MessageListeners {

    private final SqsAsyncClient sqsAsyncClient;
    private final Tracing tracing;

    @SuppressWarnings("unused")
    @QueueListener(value = "test")
    @NewSpan("wrapped-message-processing")
    public void processingMessage(Message message) throws InterruptedException {
        log.info("Waiting one second");

        Thread.sleep(1000);
        final ScopedSpan span = tracing.tracer().startScopedSpan("some-processing");
        try {
            Thread.sleep(600);
            log.info("Processing message: {}", message.body());
        } finally {
            span.finish();
        }

        Thread.sleep(1000);
    }

    @Scheduled(fixedRate = 10_000)
    public void sendMessageToQueue() throws Exception {
        log.info("Sending message");

        final ScopedSpan span = tracing.tracer().startScopedSpan("get-queue-url");
        final String queueUrl;
        try {
            // we block so when we send the message we are in the same trace context
            queueUrl =
                sqsAsyncClient
                    .getQueueUrl(builder -> builder.queueName("test"))
                    .thenApply(GetQueueUrlResponse::queueUrl)
                    .get(1, TimeUnit.MINUTES);
        } finally {
            span.finish();
        }
        sqsAsyncClient.sendMessage(builder -> builder.queueUrl(queueUrl).messageBody(UUID.randomUUID().toString()));
    }
}
