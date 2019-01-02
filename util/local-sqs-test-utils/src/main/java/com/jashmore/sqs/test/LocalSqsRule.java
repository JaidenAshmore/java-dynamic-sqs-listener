package com.jashmore.sqs.test;

import static java.util.stream.Collectors.joining;

import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

public class LocalSqsRule implements TestRule {
    private static final Logger log = LoggerFactory.getLogger(LocalSqsRule.class);
    private static final int DEFAULT_LOCALSTACK_SQS_PORT = 4576;

    private SqsAsyncClient localSqsAsyncClient;

    public LocalSqsRule() {
        this(DEFAULT_LOCALSTACK_SQS_PORT);
    }

    public LocalSqsRule(int sqsPort) {
        this.localSqsAsyncClient = new LocalSqsAsyncClient(SqsQueuesConfig
                .builder()
                .sqsServerUrl("http://localhost:" + sqsPort)
                .build());
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return base;
    }

    public SqsAsyncClient getAmazonSqsAsync() {
        return localSqsAsyncClient;
    }

    /**
     * Creates a random queue that can be used for testing, returning the URL for this queue.
     *
     * @return the queue URL of the random queue created
     */
    public String createRandomQueue() {
        final Random random = new Random();
        String queueName = IntStream.range(0, 20)
                .mapToObj(i -> random.nextInt(10))
                .map(String::valueOf)
                .collect(joining(""));

        log.info("Creating queue with name: {}", queueName);
        final Future<CreateQueueResponse> result = localSqsAsyncClient.createQueue((requestBuilder) -> requestBuilder.queueName(queueName).build());
        try {
            return result.get().queueUrl();
        } catch (InterruptedException | ExecutionException exception) {
            throw new RuntimeException(exception);
        }
    }
}
