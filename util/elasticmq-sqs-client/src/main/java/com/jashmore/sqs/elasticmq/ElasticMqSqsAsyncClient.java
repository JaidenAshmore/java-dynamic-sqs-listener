package com.jashmore.sqs.elasticmq;

import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClientImpl;
import com.jashmore.sqs.util.SqsQueuesConfig;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.http.scaladsl.Http;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class ElasticMqSqsAsyncClient implements LocalSqsAsyncClient {

    @Delegate(excludes = SdkClient.class)
    private final LocalSqsAsyncClient delegate;

    private final SQSRestServer sqsRestServer;
    private final String serverUrl;

    public ElasticMqSqsAsyncClient() {
        this(Collections.emptyList());
    }

    public ElasticMqSqsAsyncClient(final String queueName) {
        this(Collections.singletonList(SqsQueuesConfig.QueueConfig.builder().queueName(queueName).build()));
    }

    public ElasticMqSqsAsyncClient(final Consumer<SqsAsyncClientBuilder> clientBuilderConsumer) {
        this(Collections.emptyList(), clientBuilderConsumer);
    }

    public ElasticMqSqsAsyncClient(final String queueName, final Consumer<SqsAsyncClientBuilder> clientBuilderConsumer) {
        this(Collections.singletonList(SqsQueuesConfig.QueueConfig.builder().queueName(queueName).build()), clientBuilderConsumer);
    }

    public ElasticMqSqsAsyncClient(final SqsQueuesConfig.QueueConfig queueConfiguration) {
        this(Collections.singletonList(queueConfiguration), builder -> {});
    }

    public ElasticMqSqsAsyncClient(final List<SqsQueuesConfig.QueueConfig> queuesConfiguration) {
        this(queuesConfiguration, builder -> {});
    }

    public ElasticMqSqsAsyncClient(
        final List<SqsQueuesConfig.QueueConfig> queueConfigs,
        final Consumer<SqsAsyncClientBuilder> clientBuilderConsumer
    ) {
        log.info("Starting local SQS Server");
        sqsRestServer = SQSRestServerBuilder.withInterface("localhost").withDynamicPort().start();

        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();
        serverUrl = "http://localhost:" + serverBinding.localAddress().getPort();
        final SqsQueuesConfig queuesConfig = SqsQueuesConfig.builder().sqsServerUrl(serverUrl).queues(queueConfigs).build();
        delegate = new LocalSqsAsyncClientImpl(queuesConfig, clientBuilderConsumer);
    }

    @Override
    public String serviceName() {
        // Lombok is being a bit weird and I need to manually define it here
        return delegate.serviceName();
    }

    public String getServerUrl() {
        return serverUrl;
    }

    @Override
    public void close() {
        sqsRestServer.stopAndWait();
        delegate.close();
    }
}
