package com.jashmore.sqs.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;

import java.util.concurrent.ExecutionException;

/**
 * Rule that will purge all of the queues configured in the {@link SqsAsyncClient} before the test is run.
 */
@Slf4j
public class PurgeQueuesRule implements TestRule {
    private final SqsAsyncClient sqsAsyncClient;

    public PurgeQueuesRule(final SqsAsyncClient sqsAsyncClient) {
        this.sqsAsyncClient = sqsAsyncClient;
    }

    @Override
    public Statement apply(final Statement statement, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                log.info("Purging all queues");
                purgeAllQueues();
                statement.evaluate();
            }
        };
    }

    private void purgeAllQueues() throws InterruptedException, ExecutionException {
        final ListQueuesResponse listQueuesResponse = sqsAsyncClient.listQueues().get();

        for (final String queueUrl : listQueuesResponse.queueUrls()) {
            log.debug("Purging queue: {}", queueUrl);
            sqsAsyncClient.purgeQueue(builder -> builder.queueUrl(queueUrl)).get();
        }
    }
}
