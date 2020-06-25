package com.jashmore.sqs.examples;

import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    @Bean
    public LocalSqsAsyncClient sqsAsyncClient() {
        return new ElasticMqSqsAsyncClient("queue-name");
    }
}
