package com.jashmore.sqs.container.batching;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.processor.LambdaMessageProcessor;
import com.jashmore.sqs.util.CreateRandomQueueResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class BatchingMessageListenerContainerIntegrationTest {
    ElasticMqSqsAsyncClient sqsAsyncClient = new ElasticMqSqsAsyncClient();

    @Test
    void canConsumeMessages() throws Exception {
        // arrange
        final CreateRandomQueueResponse response = sqsAsyncClient.createRandomQueue().get();
        final String queueUrl = response.getResponse().queueUrl();
        final QueueProperties queueProperties = QueueProperties.builder().queueUrl(queueUrl).build();
        final CountDownLatch latch = new CountDownLatch(1);
        final MessageListenerContainer container = new BatchingMessageListenerContainer(
            "id",
            queueProperties,
            sqsAsyncClient,
            () -> new LambdaMessageProcessor(sqsAsyncClient, queueProperties, message -> latch.countDown()),
            ImmutableBatchingMessageListenerContainerProperties
                .builder()
                .concurrencyLevel(2)
                .batchSize(5)
                .batchingPeriod(Duration.ofSeconds(2))
                .build()
        );

        // act
        container.start();
        sqsAsyncClient.sendMessage(response.getQueueName(), "body");

        // assert
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        container.stop();
    }
}
