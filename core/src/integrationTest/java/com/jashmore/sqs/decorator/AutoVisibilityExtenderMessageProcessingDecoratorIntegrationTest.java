package com.jashmore.sqs.decorator;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.CoreMessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.StaticCoreMessageListenerContainerProperties;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.processor.DecoratingMessageProcessor;
import com.jashmore.sqs.processor.LambdaMessageProcessor;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.retriever.batching.BatchingMessageRetriever;
import com.jashmore.sqs.retriever.batching.StaticBatchingMessageRetrieverProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoVisibilityExtenderMessageProcessingDecoratorIntegrationTest {

    private final Logger log = LoggerFactory.getLogger(AutoVisibilityExtenderMessageProcessingDecoratorIntegrationTest.class);
    static final ElasticMqSqsAsyncClient ELASTIC_MQ_SQS_ASYNC_CLIENT = new ElasticMqSqsAsyncClient();

    @AfterAll
    static void shutdown() {
        ELASTIC_MQ_SQS_ASYNC_CLIENT.close();
    }

    @Test
    void handlesMessagesThatTakeLongPeriodsToProcess() throws Exception {
        // arrange
        final int numberOfMessages = 20;
        final AtomicInteger successfulMessages = new AtomicInteger();
        final Random random = new Random();
        final CountDownLatch latch = new CountDownLatch(numberOfMessages);
        final String listenerIdentifier = "id";
        final QueueProperties queueProperties = QueueProperties
            .builder()
            .queueUrl(ELASTIC_MQ_SQS_ASYNC_CLIENT.createRandomQueue().get().queueUrl())
            .build();
        final Set<String> messages = ConcurrentHashMap.newKeySet();
        final MessageListenerContainer container = new CoreMessageListenerContainer(
            listenerIdentifier,
            () -> new ConcurrentMessageBroker(StaticConcurrentMessageBrokerProperties.builder().concurrencyLevel(3).build()),
            () ->
                new BatchingMessageRetriever(
                    queueProperties,
                    ELASTIC_MQ_SQS_ASYNC_CLIENT,
                    StaticBatchingMessageRetrieverProperties
                        .builder()
                        .batchSize(2)
                        .batchingPeriod(Duration.ofSeconds(2))
                        .messageVisibilityTimeout(Duration.ofSeconds(3))
                        .build()
                ),
            () ->
                new DecoratingMessageProcessor(
                    listenerIdentifier,
                    queueProperties,
                    Collections.singletonList(
                        new AutoVisibilityExtenderMessageProcessingDecorator(
                            ELASTIC_MQ_SQS_ASYNC_CLIENT,
                            queueProperties,
                            new AutoVisibilityExtenderMessageProcessingDecoratorProperties() {
                                @Override
                                public Duration visibilityTimeout() {
                                    return Duration.ofSeconds(3);
                                }

                                @Override
                                public Duration maxDuration() {
                                    return Duration.ofSeconds(6);
                                }
                            }
                        )
                    ),
                    new LambdaMessageProcessor(
                        ELASTIC_MQ_SQS_ASYNC_CLIENT,
                        queueProperties,
                        message -> {
                            final int timeToWaitInMs = 2000 + random.nextInt(6000);
                            log.info("Sleeping for {}ms", timeToWaitInMs);
                            try {
                                Thread.sleep(timeToWaitInMs);
                                if (timeToWaitInMs < 6000) {
                                    log.info("Successfully finished processing");
                                    successfulMessages.incrementAndGet();
                                } else {
                                    log.error("Did not interrupt when suppose to");
                                }
                            } catch (InterruptedException e) {
                                if (timeToWaitInMs >= 6000) {
                                    log.info("Successfully interrupted when expected");
                                    successfulMessages.incrementAndGet();
                                } else {
                                    log.error("Interrupted when not suppose to");
                                }
                            } finally {
                                messages.add(message.body());
                                latch.countDown();
                            }
                        }
                    )
                ),
            () -> new BatchingMessageResolver(queueProperties, ELASTIC_MQ_SQS_ASYNC_CLIENT)
        );

        IntStream
            .range(0, numberOfMessages)
            .forEach(
                i -> ELASTIC_MQ_SQS_ASYNC_CLIENT.sendMessage(builder -> builder.queueUrl(queueProperties.getQueueUrl()).messageBody("" + i))
            );

        // act
        container.start();
        assertThat(latch.await(2, TimeUnit.MINUTES)).isTrue();
        container.stop();

        // assert
        assertThat(successfulMessages.get()).isEqualTo(numberOfMessages);
        assertThat(messages).hasSize(numberOfMessages);
    }

    @Test
    void startingAndStoppingContainerWillStillWorkWithDecorator() throws Exception {
        // arrange
        final int numberOfMessages = 20;
        final AtomicInteger successfulMessages = new AtomicInteger();
        final Random random = new Random();
        final CountDownLatch latch = new CountDownLatch(numberOfMessages);
        final CountDownLatch halfWayLatch = new CountDownLatch(numberOfMessages / 2);
        final String listenerIdentifier = "id";
        final QueueProperties queueProperties = QueueProperties
            .builder()
            .queueUrl(ELASTIC_MQ_SQS_ASYNC_CLIENT.createRandomQueue().get().queueUrl())
            .build();
        final MessageListenerContainer container = new CoreMessageListenerContainer(
            listenerIdentifier,
            () -> new ConcurrentMessageBroker(StaticConcurrentMessageBrokerProperties.builder().concurrencyLevel(3).build()),
            () ->
                new BatchingMessageRetriever(
                    queueProperties,
                    ELASTIC_MQ_SQS_ASYNC_CLIENT,
                    StaticBatchingMessageRetrieverProperties
                        .builder()
                        .batchSize(2)
                        .batchingPeriod(Duration.ofSeconds(2))
                        .messageVisibilityTimeout(Duration.ofSeconds(3))
                        .build()
                ),
            () ->
                new DecoratingMessageProcessor(
                    listenerIdentifier,
                    queueProperties,
                    Collections.singletonList(
                        new AutoVisibilityExtenderMessageProcessingDecorator(
                            ELASTIC_MQ_SQS_ASYNC_CLIENT,
                            queueProperties,
                            new AutoVisibilityExtenderMessageProcessingDecoratorProperties() {
                                @Override
                                public Duration visibilityTimeout() {
                                    return Duration.ofSeconds(3);
                                }

                                @Override
                                public Duration maxDuration() {
                                    return Duration.ofSeconds(6);
                                }
                            }
                        )
                    ),
                    new LambdaMessageProcessor(
                        ELASTIC_MQ_SQS_ASYNC_CLIENT,
                        queueProperties,
                        message -> {
                            final int timeToWaitInMs = 2000 + random.nextInt(6000);
                            log.info("Sleeping for {}ms", timeToWaitInMs);
                            try {
                                Thread.sleep(timeToWaitInMs);
                                if (timeToWaitInMs < 6000) {
                                    log.info("Successfully finished processing");
                                    successfulMessages.incrementAndGet();
                                } else {
                                    log.error("Did not interrupt when suppose to");
                                }
                            } catch (InterruptedException e) {
                                if (timeToWaitInMs >= 6000) {
                                    log.info("Successfully interrupted when expected");
                                    successfulMessages.incrementAndGet();
                                } else {
                                    log.error("Interrupted when not suppose to");
                                }
                            } finally {
                                halfWayLatch.countDown();
                                latch.countDown();
                            }
                        }
                    )
                ),
            () -> new BatchingMessageResolver(queueProperties, ELASTIC_MQ_SQS_ASYNC_CLIENT),
            StaticCoreMessageListenerContainerProperties
                .builder()
                .shouldInterruptThreadsProcessingMessagesOnShutdown(false)
                .shouldProcessAnyExtraRetrievedMessagesOnShutdown(true)
                .build()
        );
        for (int i = 0; i < numberOfMessages; ++i) {
            ELASTIC_MQ_SQS_ASYNC_CLIENT.sendMessage(builder -> builder.queueUrl(queueProperties.getQueueUrl()).messageBody("body"));
        }

        // act
        container.start();
        assertThat(halfWayLatch.await(2, TimeUnit.MINUTES)).isTrue();
        container.stop();
        container.start();
        assertThat(latch.await(2, TimeUnit.MINUTES)).isTrue();
        container.stop();

        // assert
        assertThat(successfulMessages.get()).isEqualTo(numberOfMessages);
    }
}
