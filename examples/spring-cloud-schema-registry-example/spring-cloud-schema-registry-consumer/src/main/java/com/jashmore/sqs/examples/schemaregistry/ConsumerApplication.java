package com.jashmore.sqs.examples.schemaregistry;

import com.example.Sensor;
import com.jashmore.sqs.extensions.registry.EnableSchemaRegistrySqsExtension;
import com.jashmore.sqs.extensions.registry.SpringRegistryPayload;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.schema.registry.client.EnableSchemaRegistryClient;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Slf4j
@SpringBootApplication
@EnableSchemaRegistryClient
@EnableSchemaRegistrySqsExtension
@SuppressWarnings("checkstyle:javadocmethod")
public class ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class);
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return new LocalSqsAsyncClient(SqsQueuesConfig.builder()
                .sqsServerUrl("http://localhost:9324")
                .queue(SqsQueuesConfig.QueueConfig.builder()
                        .queueName("test")
                        .deadLetterQueueName("test-dlq")
                        .maxReceiveCount(3)
                        .build())
                .build());
    }

    @QueueListener(value = "test", identifier = "message-listener")
    public void listen(@SpringRegistryPayload Sensor payload) {
        log.info("Payload: {}", payload);
    }
}
