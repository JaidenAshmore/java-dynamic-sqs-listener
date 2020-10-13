package com.jashmore.sqs.examples;

import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@EnableScheduling
@SpringBootApplication
public class Application {

    static final String QUEUE_NAME = "queue";

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return new ElasticMqSqsAsyncClient(
            SqsQueuesConfig.QueueConfig
                .builder()
                .queueName(QUEUE_NAME)
                .deadLetterQueueName("queue-dlq")
                .maxReceiveCount(3)
                .visibilityTimeout(5)
                .build()
        );
    }
}
