package com.jashmore.sqs.examples;

import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return new ElasticMqSqsAsyncClient("myQueueName");
    }
}
