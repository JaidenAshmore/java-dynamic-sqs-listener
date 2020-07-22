package com.jashmore.sqs.examples;

import static java.util.stream.Collectors.toSet;

import brave.Tracing;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.CoreArgumentResolverService;
import com.jashmore.sqs.argument.messageid.MessageId;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.CoreMessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.extensions.brave.decorator.BraveMessageProcessingDecorator;
import com.jashmore.sqs.processor.CoreMessageProcessor;
import com.jashmore.sqs.processor.DecoratingMessageProcessor;
import com.jashmore.sqs.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.resolver.batching.StaticBatchingMessageResolverProperties;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.StaticPrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * This example shows the core framework being used to processing messages place onto the queue with a dynamic level of concurrency via the
 * {@link ConcurrentMessageBroker}. The rate of concurrency is randomly changed every 10 seconds to a new value between 0 and 10.
 *
 * <p>This example will also show how the performance of the message processing can be improved by prefetching messages via the
 * {@link PrefetchingMessageRetriever}.
 *
 * <p>While this is running you should see the messages being processed and the number of messages that are concurrently being processed. This will highlight
 * how the concurrency can change during the running of the application.
 */
@Slf4j
public class ConcurrentBrokerExample {
    private static final long CONCURRENCY_LEVEL_PERIOD_IN_MS = 5000L;
    private static final int CONCURRENCY_LEVEL_LIMIT = 10;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(final String[] args) throws Exception {
        // Sets up the SQS that will be used
        final LocalSqsAsyncClient sqsAsyncClient = new ElasticMqSqsAsyncClient();
        final String queueUrl = sqsAsyncClient.createRandomQueue().get().queueUrl();
        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(queueUrl)
                .build();

        final MessageConsumer messageConsumer = new MessageConsumer();
        final Method messageReceivedMethod;
        try {
            messageReceivedMethod = MessageConsumer.class.getMethod("method", Request.class, String.class);
        } catch (final NoSuchMethodException exception) {
            throw new RuntimeException(exception);
        }

        final Tracing tracing = Tracing.newBuilder()
                .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
                        .addScopeDecorator(MDCScopeDecorator.get())
                        .build()
                )
                .build();
        tracing.setNoop(true);

        final String identifier = "core-example-container";
        final MessageListenerContainer container = new CoreMessageListenerContainer(
                identifier,
                () -> new ConcurrentMessageBroker(new ConcurrentMessageBrokerProperties() {
                    private final Random random = new Random();
                    private final LoadingCache<Boolean, Integer> cachedConcurrencyLevel = CacheBuilder.newBuilder()
                            .expireAfterWrite(10, TimeUnit.SECONDS)
                            .build(CacheLoader.from(() -> random.nextInt(CONCURRENCY_LEVEL_LIMIT)));

                    @Override
                    public @PositiveOrZero int getConcurrencyLevel() {
                        return cachedConcurrencyLevel.getUnchecked(true);
                    }

                    @Override
                    public Duration getConcurrencyPollingRate() {
                        return Duration.ofMillis(CONCURRENCY_LEVEL_PERIOD_IN_MS);
                    }

                    @Override
                    public Duration getErrorBackoffTime() {
                        return Duration.ofMillis(500);
                    }
                }),
                () -> new PrefetchingMessageRetriever(
                        sqsAsyncClient,
                        queueProperties,
                        StaticPrefetchingMessageRetrieverProperties.builder()
                                .desiredMinPrefetchedMessages(10)
                                .maxPrefetchedMessages(20)
                                .build()
                ),
                () -> new DecoratingMessageProcessor(
                        identifier,
                        queueProperties,
                        Collections.singletonList(new BraveMessageProcessingDecorator(tracing)),
                        new CoreMessageProcessor(
                                new CoreArgumentResolverService(new JacksonPayloadMapper(OBJECT_MAPPER), OBJECT_MAPPER),
                                queueProperties,
                                sqsAsyncClient,
                                messageReceivedMethod,
                                messageConsumer
                        )
                ),
                () -> new BatchingMessageResolver(queueProperties, sqsAsyncClient,
                        StaticBatchingMessageResolverProperties.builder()
                                .bufferingSizeLimit(1)
                                .bufferingTime(Duration.ofSeconds(5))
                                .build())
        );
        container.start();

        log.info("Started container");

        final AtomicInteger count = new AtomicInteger(0);
        final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            sqsAsyncClient.sendMessageBatch((builder) -> builder
                    .queueUrl(queueUrl)
                    .entries(IntStream.range(0, 10)
                            .mapToObj(index -> {
                                final Request request = new Request("key_" + count.getAndIncrement());
                                try {
                                    return SendMessageBatchRequestEntry.builder()
                                            .id("" + index)
                                            .messageBody(OBJECT_MAPPER.writeValueAsString(request))
                                            .build();
                                } catch (JsonProcessingException exception) {
                                    throw new RuntimeException(exception);
                                }

                            })
                            .collect(toSet())
                    ));
            log.info("Put 10 messages onto queue");
        }, 0, 2, TimeUnit.SECONDS);

        log.info("Running application for 3 minutes. Ctrl + C to exit...");
        Thread.sleep(3 * 60 * 1000);
        scheduledExecutorService.shutdownNow();
    }

    @Data
    @AllArgsConstructor
    private static class Request {
        private final String key;
    }

    @Slf4j
    @SuppressWarnings("WeakerAccess")
    public static class MessageConsumer {
        private static final AtomicInteger concurrentMessagesBeingProcessed = new AtomicInteger(0);
        private final Random processingTimeRandom = new Random();

        public void method(@Payload final Request request, @MessageId final String messageId) throws InterruptedException {
            try {
                final int concurrentMessages = concurrentMessagesBeingProcessed.incrementAndGet();
                int processingTimeInMs = processingTimeRandom.nextInt(3000);
                log.trace("Payload: {}, messageId: {}", request, messageId);
                log.info("Message processing in {}ms. {} currently being processed concurrently", processingTimeInMs, concurrentMessages);
                Thread.sleep(processingTimeInMs);
            } finally {
                concurrentMessagesBeingProcessed.decrementAndGet();
            }
        }
    }
}
