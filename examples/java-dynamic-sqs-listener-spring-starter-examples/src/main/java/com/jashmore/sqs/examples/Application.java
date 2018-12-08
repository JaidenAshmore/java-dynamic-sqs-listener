package com.jashmore.sqs.examples;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.jashmore.sqs.annotation.EnableQueueListeners;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.broker.concurrent.properties.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.custom.MessageBrokerFactory;
import com.jashmore.sqs.container.custom.MessageProcessorFactory;
import com.jashmore.sqs.container.custom.MessageRetrieverFactory;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingProperties;
import com.jashmore.sqs.util.LocalAmazonSqsAsync;
import com.jashmore.sqs.util.SqsQueuesConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executors;

@EnableQueueListeners
@SpringBootApplication
@ComponentScan("com.jashmore.sqs.examples")
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    /**
     * Connects to localstack.
     */
    @Bean
    public AmazonSQSAsync amazonSqsAsync() {
        return new LocalAmazonSqsAsync(SqsQueuesConfig
                .builder()
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName("test").build())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName("anotherTest").build())
                .build());
    }

    /**
     * Example of a {@link MessageRetrieverFactory} being built that will be used by the {@link com.jashmore.sqs.container.custom.CustomQueueListener}.
     */
    @Bean
    public MessageRetrieverFactory myMessageRetrieverFactory(final AmazonSQSAsync amazonSqsAsync) {
        return (queueProperties) -> {
            final PrefetchingProperties prefetchingProperties = PrefetchingProperties
                    .builder()
                    .maxPrefetchedMessages(10)
                    .desiredMinPrefetchedMessages(0)
                    .maxWaitTimeInSecondsToObtainMessagesFromServer(10)
                    .visibilityTimeoutForMessagesInSeconds(30)
                    .errorBackoffTimeInMilliseconds(10)
                    .build();
            return new PrefetchingMessageRetriever(amazonSqsAsync, queueProperties, prefetchingProperties, Executors.newCachedThreadPool());
        };
    }

    /**
     * Example of a {@link MessageProcessorFactory} being built that will be used by the {@link com.jashmore.sqs.container.custom.CustomQueueListener}.
     */
    @Bean
    public MessageProcessorFactory myMessageProcessorFactory(final ArgumentResolverService argumentResolverService,
                                                             final AmazonSQSAsync amazonSqsAsync) {
        return (queueProperties, bean, method) -> new DefaultMessageProcessor(argumentResolverService, queueProperties, amazonSqsAsync, method, bean);
    }

    /**
     * Example of a {@link MessageBrokerFactory} being built that will be used by the {@link com.jashmore.sqs.container.custom.CustomQueueListener}.
     */
    @Bean
    public MessageBrokerFactory myMessageBrokerFactory() {
        return (messageRetriever, messageProcessor) -> {
            final ConcurrentMessageBrokerProperties properties = StaticConcurrentMessageBrokerProperties
                    .builder()
                    .concurrencyLevel(2)
                    .preferredConcurrencyPollingRateInMilliseconds(1000)
                    .build();
            return new ConcurrentMessageBroker(messageRetriever, messageProcessor, Executors.newCachedThreadPool(), properties);
        };
    }
}
