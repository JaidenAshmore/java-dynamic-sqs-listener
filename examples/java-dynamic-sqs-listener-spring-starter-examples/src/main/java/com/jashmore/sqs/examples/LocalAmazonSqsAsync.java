package com.jashmore.sqs.examples;

import ch.qos.logback.core.util.ExecutorServiceUtil;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import javax.annotation.PostConstruct;

@Slf4j
public class LocalAmazonSqsAsync implements AmazonSQSAsync {
    @Delegate
    private final AmazonSQSAsync delegate;

    public LocalAmazonSqsAsync() {
        log.info("Starting Local ElasticMQ SQS Server");

        delegate = AmazonSQSAsyncClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:4576", "localstack"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
                .build();
    }

    @PostConstruct
    public void setupQueues() {
        // setup
        final CreateQueueResult queueResult = delegate.createQueue("test");


        Executors.newSingleThreadExecutor().submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                log.info("Putting message onto queue");
                delegate.sendMessage(queueResult.getQueueUrl(), "message");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }
}
