package com.jashmore.sqs.examples;

import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@EnableScheduling
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    /**
     * Connects to an internal ElasticMQ SQS Server, this will replace the {@link SqsAsyncClient} provided by
     * {@link QueueListenerConfiguration#sqsAsyncClient()}.
     *
     * @return client used for communicating a local in-memory SQS
     */
    @Bean
    public LocalSqsAsyncClient sqsAsyncClient() {
        return new ElasticMqSqsAsyncClient("queue-name");
    }
}
