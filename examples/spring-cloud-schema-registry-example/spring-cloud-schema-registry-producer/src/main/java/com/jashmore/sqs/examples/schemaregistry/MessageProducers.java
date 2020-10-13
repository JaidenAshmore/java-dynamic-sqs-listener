package com.jashmore.sqs.examples.schemaregistry;

import com.example.Sensor;
import com.jashmore.sqs.registry.AvroSchemaRegistrySqsAsyncClient;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:javadocmethod")
public class MessageProducers {

    private final AvroSchemaRegistrySqsAsyncClient sqsAsyncClient;

    @Scheduled(initialDelay = 1000, fixedDelay = 1000)
    public void addMessages() {
        final Sensor payload = new Sensor("V1-" + UUID.randomUUID().toString(), 1.0F, 1.1F, 1.2F);

        sqsAsyncClient
            .getQueueUrl(request -> request.queueName("test"))
            .thenApply(GetQueueUrlResponse::queueUrl)
            .thenCompose(
                queueUrl ->
                    sqsAsyncClient.sendAvroMessage(
                        "ProducerV1",
                        "contentType",
                        payload,
                        requestBuilder -> requestBuilder.queueUrl(queueUrl)
                    )
            )
            .whenComplete(
                (result, throwable) -> {
                    if (throwable != null) {
                        log.error("Error sending message", throwable);
                    }
                    log.info("Published message with id {}", payload.getId());
                }
            );
    }
}
