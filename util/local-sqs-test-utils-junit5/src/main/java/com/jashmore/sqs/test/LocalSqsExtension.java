package com.jashmore.sqs.test;

import com.google.common.collect.ImmutableList;

import akka.http.scaladsl.Http;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import lombok.extern.slf4j.Slf4j;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Test Rule that is builds an in-memory ElasticMQ SQS Server for usage in testing.
 *
 * <p>When using this for an integration test with a spring application this must be a and the built
 * {@link SqsAsyncClient} should be included into the context. For example:
 *
 * <pre class="code">
 * &#064;SpringBootTest(classes = {Application.class, IntegrationTest.TestConfig.class})
 * &#064;RunWith(SpringRunner.class)
 * public class IntegrationTest {
 *     &#064;ClassRule
 *     public static final LocalSqsExtension LOCAL_SQS_RULE = new LocalSqsExtension(ImmutableList.of(
 *         // all of the queues needed to be set up for the test
 *     ));
 *
 *     &#064;Configuration
 *     public static class TestConfig {
 *         &#064;Bean
 *         public LocalSqsAsyncClient localSqsAsyncClient() {
 *             return LOCAL_SQS_RULE.getLocalAmazonSqsAsync();
 *         }
 *     }
 * }
 * </pre>
 */
@Slf4j
public class LocalSqsExtension implements BeforeAllCallback, AfterEachCallback, AfterAllCallback {
    private final List<SqsQueuesConfig.QueueConfig> queuesConfiguration;

    private LocalSqsAsyncClient localSqsAsyncClient;
    private String queueServerUrl;
    private SQSRestServer sqsRestServer;

    public LocalSqsExtension() {
        this.queuesConfiguration = ImmutableList.of();
    }


    public LocalSqsExtension(final String queueName) {
        this(SqsQueuesConfig.QueueConfig.builder().queueName(queueName).build());
    }

    public LocalSqsExtension(final SqsQueuesConfig.QueueConfig queueConfiguration) {
        this.queuesConfiguration = ImmutableList.of(queueConfiguration);
    }

    public LocalSqsExtension(final List<SqsQueuesConfig.QueueConfig> queuesConfiguration) {
        this.queuesConfiguration = queuesConfiguration;
    }

    @Override
    public void beforeAll(final ExtensionContext context) {
        log.info("Starting local SQS Server");
        sqsRestServer = SQSRestServerBuilder
                .withInterface("localhost")
                .withDynamicPort()
                .start();

        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();
        queueServerUrl = "http://localhost:" + serverBinding.localAddress().getPort();
        localSqsAsyncClient = new LocalSqsAsyncClient(SqsQueuesConfig
                .builder()
                .sqsServerUrl(queueServerUrl)
                .queues(queuesConfiguration)
                .build());
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        log.info("Shutting down local SQS Server");
        sqsRestServer.stopAndWait();
        queueServerUrl = null;
        localSqsAsyncClient = null;
    }

    public LocalSqsAsyncClient getLocalAmazonSqsAsync() {
        return localSqsAsyncClient;
    }

    /**
     * Get the Server URL of the ElasticMQ server built.
     *
     * @return the Server URL of the SQS server
     */
    public String getServerUrl() {
        return queueServerUrl;
    }

    /**
     * Creates a random queue that can be used for testing, returning the URL for this queue.
     *
     * @return the queue URL of the random queue created
     */
    public String createRandomQueue() {
        final String queueName = UUID.randomUUID().toString().replace("-", "");

        log.info("Creating queue with name: {}", queueName);
        final Future<CreateQueueResponse> result = localSqsAsyncClient.createQueue((requestBuilder) -> requestBuilder.queueName(queueName).build());
        try {
            return result.get().queueUrl();
        } catch (InterruptedException | ExecutionException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void afterEach(final ExtensionContext context) throws Exception {
        log.info("Purging all queues");
        purgeAllQueues();
    }

    private void purgeAllQueues() throws InterruptedException, ExecutionException {
        final ListQueuesResponse listQueuesResponse = localSqsAsyncClient.listQueues().get();

        for (final String queueUrl : listQueuesResponse.queueUrls()) {
            log.debug("Purging queue: {}", queueUrl);
            localSqsAsyncClient.purgeQueue(builder -> builder.queueUrl(queueUrl)).get();
        }
    }
}
