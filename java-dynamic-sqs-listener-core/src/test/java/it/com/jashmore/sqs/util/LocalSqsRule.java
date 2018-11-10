package it.com.jashmore.sqs.util;

import static java.util.stream.Collectors.joining;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.stream.IntStream;

public class LocalSqsRule implements TestRule {
    private static final Logger log = LoggerFactory.getLogger(LocalSqsRule.class);
    private static final int DEFAULT_LOCALSTACK_SQS_PORT = 4576;

    private AmazonSQSAsync localAmazonSqsAsync;

    public LocalSqsRule() {
        this(DEFAULT_LOCALSTACK_SQS_PORT);
    }

    public LocalSqsRule(int sqsPort) {
        this.localAmazonSqsAsync = AmazonSQSAsyncClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:" + sqsPort, "localstack"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
                .build();
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();
            }
        };
    }

    public AmazonSQSAsync getAmazonSqsAsync() {
        return localAmazonSqsAsync;
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
        final CreateQueueResult result = localAmazonSqsAsync.createQueue(queueName);
        return result.getQueueUrl();
    }
}
