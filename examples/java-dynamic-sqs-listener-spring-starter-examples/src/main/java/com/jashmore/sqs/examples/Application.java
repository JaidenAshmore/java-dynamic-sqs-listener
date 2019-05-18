package com.jashmore.sqs.examples;

import akka.http.scaladsl.Http;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import lombok.extern.slf4j.Slf4j;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * This example creates two queues using a local in-memory ElasticMQ server which are listened again by the {@link MessageListeners} class. Two examples
 * of setting up message listeners are provided:
 * <ul>
 *     <li>{@link MessageListeners#method(String)} uses a {@link QueueListener @QueueListener} to listen to messages concurrently with prefetching enabled</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@Slf4j
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
    public SqsAsyncClient sqsAsyncClient() {
        log.info("Starting Local ElasticMQ SQS Server");
        final SQSRestServer sqsRestServer = SQSRestServerBuilder
                .withInterface("localhost")
                .withDynamicPort()
                .start();

        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();
        return new LocalSqsAsyncClient(SqsQueuesConfig
                .builder()
                .sqsServerUrl("http://localhost:" + serverBinding.localAddress().getPort())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName("test").build())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName("anotherTest").build())
                .build());
    }
}
