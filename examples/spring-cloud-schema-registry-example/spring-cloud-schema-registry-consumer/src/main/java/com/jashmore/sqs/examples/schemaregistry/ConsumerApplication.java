package com.jashmore.sqs.examples.schemaregistry;

import com.example.Sensor;
import com.jashmore.sqs.extensions.registry.SpringCloudSchemaRegistryPayload;
import com.jashmore.sqs.extensions.registry.avro.EnableSchemaRegistrySqsExtension;
import com.jashmore.sqs.annotations.core.basic.QueueListener;
import com.jashmore.sqs.util.LocalSqsAsyncClientImpl;
import com.jashmore.sqs.util.SqsQueuesConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Slf4j
@SpringBootApplication
@EnableSchemaRegistrySqsExtension
@SuppressWarnings("checkstyle:javadocmethod")
public class ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class);
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return new LocalSqsAsyncClientImpl(
            SqsQueuesConfig
                .builder()
                .sqsServerUrl("http://localhost:9324")
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName("test").deadLetterQueueName("test-dlq").maxReceiveCount(3).build())
                .build()
        );
    }

    @SuppressWarnings("unused")
    @QueueListener(value = "test", identifier = "message-listener")
    public void listen(@SpringCloudSchemaRegistryPayload Sensor payload) {
        log.info("Payload: {}", payload);
    }
}
