package com.jashmore.sqs.examples;

import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.client.DefaultSqsAsyncClientProvider;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    /**
     * Creates the {@link SqsAsyncClientProvider} that will allow multiple AWS Account SQS queues to be queried.
     *
     * @return the provider to use
     * @throws InterruptedException if it was interrupted while building the clients
     */
    @Bean
    public SqsAsyncClientProvider sqsAsyncClientProvider() throws InterruptedException {
        final Map<String, SqsAsyncClient> clientsMap = new HashMap<>();
        clientsMap.put("firstClient", new ElasticMqSqsAsyncClient("firstClientQueue"));
        clientsMap.put("secondClient", new ElasticMqSqsAsyncClient("secondClientQueue"));
        return new DefaultSqsAsyncClientProvider(null, clientsMap);
    }
}
