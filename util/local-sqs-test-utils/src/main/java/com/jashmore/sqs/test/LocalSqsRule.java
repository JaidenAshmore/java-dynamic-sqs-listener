package com.jashmore.sqs.test;

import com.google.common.collect.ImmutableList;

import akka.http.scaladsl.Http;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import lombok.extern.slf4j.Slf4j;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Test Rule that is builds an in-memory ElasticMQ SQS Server for usage in testing.
 *
 * <p>When using this for an integration test with a spring application this must be a {@link org.junit.ClassRule @ClassRule} and the built
 * {@link SqsAsyncClient} should be included into the context. For example:
 *
 * <pre class="code">
 * &#064;SpringBootTest(classes = {Application.class, IntegrationTest.TestConfig.class}, webEnvironment = RANDOM_PORT)
 * &#064;RunWith(SpringRunner.class)
 * public class IntegrationTest {
 *     &#064;ClassRule
 *     public static final LocalSqsRule LOCAL_SQS_RULE = new LocalSqsRule(ImmutableList.of(
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
public class LocalSqsRule implements TestRule {
    private final List<SqsQueuesConfig.QueueConfig> queuesConfiguration;

    private LocalSqsAsyncClient localSqsAsyncClient;
    private String queueServerUrl;

    public LocalSqsRule() {
        this.queuesConfiguration = ImmutableList.of();
    }

    public LocalSqsRule(final List<SqsQueuesConfig.QueueConfig> queuesConfiguration) {
        this.queuesConfiguration = queuesConfiguration;
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                log.info("Starting local SQS Server");
                final SQSRestServer sqsRestServer = SQSRestServerBuilder
                        .withInterface("localhost")
                        .withDynamicPort()
                        .start();

                final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();
                queueServerUrl = "http://localhost:" + serverBinding.localAddress().getPort();
                try {
                    localSqsAsyncClient = new LocalSqsAsyncClient(SqsQueuesConfig
                            .builder()
                            .sqsServerUrl(queueServerUrl)
                            .queues(queuesConfiguration)
                            .build());
                    base.evaluate();
                } finally {
                    log.info("Shutting down local SQS Server");
                    queueServerUrl = null;
                    localSqsAsyncClient = null;
                }
            }
        };
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
}
