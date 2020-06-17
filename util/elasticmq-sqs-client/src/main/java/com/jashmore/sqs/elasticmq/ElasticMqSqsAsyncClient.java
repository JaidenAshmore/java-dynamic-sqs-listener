package com.jashmore.sqs.elasticmq;

import akka.http.scaladsl.Http;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClientImpl;
import com.jashmore.sqs.util.SqsQueuesConfig;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import software.amazon.awssdk.core.SdkClient;

import java.util.Collections;
import java.util.List;

@Slf4j
public class ElasticMqSqsAsyncClient implements LocalSqsAsyncClient {

    @Delegate(excludes = SdkClient.class)
    private final LocalSqsAsyncClient delegate;

    private final SQSRestServer sqsRestServer;

    public ElasticMqSqsAsyncClient() {
        this(Collections.emptyList());
    }

    public ElasticMqSqsAsyncClient(final String queueName) {
        this(Collections.singletonList(SqsQueuesConfig.QueueConfig.builder()
                .queueName(queueName)
                .build()));
    }

    public ElasticMqSqsAsyncClient(final List<SqsQueuesConfig.QueueConfig> queuesConfiguration) {
        log.info("Starting local SQS Server");
        sqsRestServer = SQSRestServerBuilder
                .withInterface("localhost")
                .withDynamicPort()
                .start();

        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();
        final SqsQueuesConfig queuesConfig = SqsQueuesConfig
                .builder()
                .sqsServerUrl("http://localhost:" + serverBinding.localAddress().getPort())
                .queues(queuesConfiguration)
                .build();
        delegate = new LocalSqsAsyncClientImpl(queuesConfig);
    }

    @Override
    public String serviceName() {
        // Lombok is being a bit weird and I need to manually define it here
        return delegate.serviceName();
    }

    @Override
    public void close() {
        sqsRestServer.stopAndWait();
        delegate.close();
    }
}
