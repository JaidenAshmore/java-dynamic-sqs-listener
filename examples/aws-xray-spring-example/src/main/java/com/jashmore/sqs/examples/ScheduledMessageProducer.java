package com.jashmore.sqs.examples;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Segment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper scheduled task that will place 10 messages onto each queue for processing by the message listeners.
 */
@Slf4j
@Component
public class ScheduledMessageProducer {
    private final SqsAsyncClient sqsAsyncClient;
    private final SnsAsyncClient snsAsyncClient;
    private final String queueUrl;
    private final String snsArn;
    private final String serviceName;
    private final AtomicInteger count = new AtomicInteger();

    public ScheduledMessageProducer(final SqsAsyncClient sqsAsyncClient,
                                    final SnsAsyncClient snsAsyncClient,
                                    @Value("${sqs.queue.url}") final String queueUrl,
                                    @Value("${sns.arn}") final String snsArn,
                                    @Value("${spring.application.name}") final String serviceName) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.snsAsyncClient = snsAsyncClient;
        this.queueUrl = queueUrl;
        this.snsArn = snsArn;
        this.serviceName = serviceName;
    }

    @Scheduled(initialDelay = 1_000, fixedDelay = 10_000)
    public void addMessageDirectlyToSqs() {
        final Segment segment = AWSXRay.beginSegment(serviceName);
        AWSXRay.beginSubsegment("send-to-sqs");
        log.info("SQS Trace ID: {}", segment.getTraceId());
        final int currentValue = count.incrementAndGet();
        sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody("from-direct-sqs-" + currentValue)
                .build());
        AWSXRay.endSubsegment();
        AWSXRay.endSegment();
    }

    @Scheduled(initialDelay = 6_000, fixedDelay = 10_000)
    public void addMessageToSns() {
        final Segment segment = AWSXRay.beginSegment(serviceName);
        AWSXRay.beginSubsegment("send-to-sns");
        log.info("SNS Trace ID: {}", segment.getTraceId());
        final String uuid = UUID.randomUUID().toString();
        snsAsyncClient.publish(builder -> builder
                .message("SNS message with UUID: " + uuid)
                .topicArn(snsArn)
        );
        AWSXRay.endSubsegment();
        AWSXRay.endSegment();
    }
}
