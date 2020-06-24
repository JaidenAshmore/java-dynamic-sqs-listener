package com.jashmore.sqs.examples;

import akka.http.scaladsl.Http;
import com.jashmore.sqs.spring.client.DefaultSqsAsyncClientProvider;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import lombok.extern.slf4j.Slf4j;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

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
        clientsMap.put("firstClient", buildClient("firstClientQueue"));
        clientsMap.put("secondClient", buildClient("secondClientQueue"));
        return new DefaultSqsAsyncClientProvider(null, clientsMap);
    }

    /**
     * Starts an in-memory ElasticMQ server and returns a {@link SqsAsyncClient} pointing to it, each call to this represents a different AWS Account.
     *
     * <p>Note that the region and credentials are hardcoded to fake values as they are not checked but connecting to an actual AWS account here will involve
     * reading environment variables or other properties.
     *
     * @param queueName the name of a queue to create in this SQS Server
     * @return the {@link SqsAsyncClient} that points to this SQS Server
     * @throws InterruptedException if it was interrupted while creating the queue
     */
    private static SqsAsyncClient buildClient(String queueName) throws InterruptedException {
        log.info("Starting Local ElasticMQ SQS Server");
        final SQSRestServer sqsRestServer = SQSRestServerBuilder
                .withInterface("localhost")
                .withDynamicPort()
                .start();

        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();
        final SqsAsyncClient client = SqsAsyncClient.builder()
                .endpointOverride(URI.create("http://localhost:" + serverBinding.localAddress().getPort()))
                .region(Region.of("localstack"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("accessKeyId", "secretAccessKey")))
                .build();
        try {
            client.createQueue(CreateQueueRequest.builder()
                    .queueName(queueName)
                    .build())
                    .get();
        } catch (ExecutionException executionException) {
            throw new RuntimeException(executionException.getCause());
        }
        return client;
    }
}
