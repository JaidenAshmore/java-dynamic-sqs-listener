package it.com.jashmore.sqs.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.CoreMessageListenerContainer;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.processor.AsyncLambdaMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever;
import com.jashmore.sqs.retriever.batching.StaticBatchingMessageRetrieverProperties;
import it.com.jashmore.sqs.util.SqsIntegrationTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class AsyncLambdaMessageProcessorIntegrationTest {

    private static final ElasticMqSqsAsyncClient client = new ElasticMqSqsAsyncClient();

    @AfterAll
    static void tearDown() {
        client.close();
    }

    @Test
    void canProcessMessageLambdas() throws ExecutionException, InterruptedException {
        // arrange
        final String queueUrl = client.createRandomQueue().get().getResponse().queueUrl();
        final QueueProperties queueProperties = QueueProperties.builder()
                .queueUrl(queueUrl)
                .build();
        final MessageResolver messageResolver = new BatchingMessageResolver(queueProperties, client);
        final CountDownLatch countDownLatch = new CountDownLatch(20);
        final MessageProcessor messageProcessor = new AsyncLambdaMessageProcessor(
                client,
                queueProperties,
                (message) -> CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException interruptedException) {
                        // ignore
                    }
                    countDownLatch.countDown();
                })
        );
        final ConcurrentMessageBroker messageBroker = new ConcurrentMessageBroker(
                StaticConcurrentMessageBrokerProperties.builder()
                        .concurrencyLevel(1)
                        .build()
        );
        final MessageRetriever messageRetriever = new BatchingMessageRetriever(
                queueProperties,
                client,
                StaticBatchingMessageRetrieverProperties.builder().batchSize(1).build()
        );
        final CoreMessageListenerContainer coreMessageListenerContainer = new CoreMessageListenerContainer(
                "id",
                () -> messageBroker,
                () -> messageRetriever,
                () -> messageProcessor,
                () -> messageResolver
        );
        coreMessageListenerContainer.start();

        // act
        SqsIntegrationTestUtils.sendNumberOfMessages(20, client, queueProperties.getQueueUrl());

        // assert
        assertThat(countDownLatch.await(20, TimeUnit.SECONDS)).isTrue();
    }
}
