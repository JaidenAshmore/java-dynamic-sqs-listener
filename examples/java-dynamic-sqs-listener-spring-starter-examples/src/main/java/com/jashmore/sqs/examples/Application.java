package com.jashmore.sqs.examples;

import akka.http.scaladsl.Http;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.CachingConcurrentMessageBrokerProperties;
import com.jashmore.sqs.broker.concurrent.properties.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingProperties;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.spring.container.custom.CustomQueueListener;
import com.jashmore.sqs.spring.container.custom.MessageBrokerFactory;
import com.jashmore.sqs.spring.container.custom.MessageProcessorFactory;
import com.jashmore.sqs.spring.container.custom.MessageRetrieverFactory;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import lombok.extern.slf4j.Slf4j;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.Random;
import java.util.concurrent.Executors;
import javax.validation.constraints.Min;

/**
 * This example creates two queues using a local in-memory ElasticMQ server which are listened again by the {@link MessageListeners} class. Two examples
 * of setting up message listeners are provided:
 * <ul>
 *     <li>{@link MessageListeners#method(String)} uses a {@link QueueListener @QueueListener} to listen to messages concurrently with prefetching enabled</li>
 *     <li>{@link MessageListeners#configurableMethod(String)} uses a {@link CustomQueueListener} to listen to messages with a dynamic ({@link Random}
 *         number of threads processing the messages</li>
 * </ul>
 */
@SpringBootApplication
@ComponentScan("com.jashmore.sqs.examples")
@EnableScheduling
@Slf4j
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    /**
     * Connects to an internal ElasticMQ SQS Server, this will replace the {@link SqsAsyncClient} provided by
     * {@link QueueListenerConfiguration#sqsAsyncClient()}.
     */
    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        log.info("Starting Local ElasticMQ SQS Server");
        final SQSRestServer sqsRestServer = SQSRestServerBuilder
                .withInterface("localhost")
                .withDynamicPort()
                .start();

        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();
        return new LocalSqsAsyncClient(SqsQueuesConfig
                .builder()
                .sqsServerUrl("http://localhost:" + serverBinding.localAddress().getPort())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName("test").build())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName("anotherTest").build())
                .build());
    }

    /**
     * Example of a {@link MessageRetrieverFactory} being built that will be used by the {@link CustomQueueListener}.
     */
    @Bean
    public MessageRetrieverFactory myMessageRetrieverFactory(final SqsAsyncClient sqsAsyncClient) {
        return (queueProperties) -> {
            final PrefetchingProperties prefetchingProperties = PrefetchingProperties
                    .builder()
                    .maxPrefetchedMessages(10)
                    .desiredMinPrefetchedMessages(0)
                    .maxWaitTimeInSecondsToObtainMessagesFromServer(10)
                    .visibilityTimeoutForMessagesInSeconds(30)
                    .errorBackoffTimeInMilliseconds(10)
                    .build();
            return new PrefetchingMessageRetriever(sqsAsyncClient, queueProperties, prefetchingProperties, Executors.newCachedThreadPool());
        };
    }

    /**
     * Example of a {@link MessageProcessorFactory} being built that will be used by the {@link CustomQueueListener}.
     */
    @Bean
    public MessageProcessorFactory myMessageProcessorFactory(final ArgumentResolverService argumentResolverService,
                                                             final SqsAsyncClient sqsAsyncClient) {
        return (queueProperties, bean, method) -> new DefaultMessageProcessor(argumentResolverService, queueProperties, sqsAsyncClient, method, bean);
    }

    /**
     * Example of a {@link MessageBrokerFactory} being built that will be used by the {@link CustomQueueListener}.
     */
    @Bean
    public MessageBrokerFactory myMessageBrokerFactory() {
        return (messageRetriever, messageProcessor) -> {
            // Here is how we can dynamically change the number of threads that are processing messages. Every time it needs to process another message it
            // will check this concurrency level to see if it needs to change. We have wrapped it with some internal caching so it only checks for an actual
            // new value every 1 second
            final ConcurrentMessageBrokerProperties properties = new CachingConcurrentMessageBrokerProperties(
                    1000, new RandomConcurrentMessageBrokerProperties());
            return new ConcurrentMessageBroker(messageRetriever, messageProcessor, Executors.newCachedThreadPool(), properties);
        };
    }

    /**
     * Implementation that will randomly change the level of concurrency every 5 seconds.
     */
    private static class RandomConcurrentMessageBrokerProperties implements ConcurrentMessageBrokerProperties {
        private final Random random = new Random(1);

        @Override
        public Integer getConcurrencyLevel() {
            return random.nextInt(5);
        }

        @Override
        public @Min(0) Integer getPreferredConcurrencyPollingRateInMilliseconds() {
            return 5000;
        }
    }
}
