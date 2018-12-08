package it.com.jashmore.sqs.container.custom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.broker.concurrent.properties.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.container.custom.CustomQueueListener;
import com.jashmore.sqs.container.custom.MessageBrokerFactory;
import com.jashmore.sqs.container.custom.MessageProcessorFactory;
import com.jashmore.sqs.container.custom.MessageRetrieverFactory;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingProperties;
import it.com.jashmore.example.Application;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Slf4j
@SpringBootTest(classes = {Application.class, CustomQueueWrapperIntegrationTest.TestConfig.class}, webEnvironment = RANDOM_PORT)
@RunWith(SpringRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class CustomQueueWrapperIntegrationTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private static final int NUMBER_OF_MESSAGES_TO_SEND = 100;
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(NUMBER_OF_MESSAGES_TO_SEND);

    private static final Map<Integer, Boolean> messagesProcessed = new ConcurrentHashMap<>();

    @Autowired
    private AmazonSQSAsync amazonSqsAsync;

    @Configuration
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public static class TestConfig {
        @Bean
        public CustomQueueWrapperIntegrationTest.MessageListener messageListener() {
            return new CustomQueueWrapperIntegrationTest.MessageListener();
        }

        @Bean
        public MessageRetrieverFactory myMessageRetrieverFactory(final AmazonSQSAsync amazonSqsAsync) {
            return (queueProperties) -> {
                final PrefetchingProperties prefetchingProperties = PrefetchingProperties
                        .builder()
                        .maxPrefetchedMessages(10)
                        .desiredMinPrefetchedMessages(0)
                        .maxWaitTimeInSecondsToObtainMessagesFromServer(5)
                        .visibilityTimeoutForMessagesInSeconds(30)
                        .errorBackoffTimeInMilliseconds(10)
                        .build();
                return new PrefetchingMessageRetriever(amazonSqsAsync, queueProperties, prefetchingProperties, Executors.newCachedThreadPool());
            };
        }

        @Bean
        public MessageProcessorFactory myMessageProcessorFactory(final ArgumentResolverService argumentResolverService,
                                                                 final AmazonSQSAsync amazonSQSAsync) {
            return (queueProperties, bean, method) -> new DefaultMessageProcessor(argumentResolverService, queueProperties, amazonSQSAsync, method, bean);
        }

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

    @Test
    public void allMessagesAreProcessedByListeners() throws InterruptedException {
        // arrange
        final String queueUrl = amazonSqsAsync.getQueueUrl("CustomQueueWrapperIntegrationTest").getQueueUrl();
        IntStream.range(0, NUMBER_OF_MESSAGES_TO_SEND)
                .parallel()
                .mapToObj(i -> {
                    log.info("Sending message: " + i);
                    return amazonSqsAsync.sendMessageAsync(queueUrl, "" + i);
                })
                .forEach(future -> {
                    try {
                        future.get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                });

        // act
        COUNT_DOWN_LATCH.await(60, TimeUnit.SECONDS);

        // assert
        final List<Integer> processed = new ArrayList<>(messagesProcessed.keySet());
        Collections.sort(processed);
        assertThat(processed).hasSize(100);
    }

    public static class MessageListener {
        @CustomQueueListener(queue = "CustomQueueWrapperIntegrationTest",
                messageBrokerFactoryBeanName = "myMessageBrokerFactory",
                messageProcessorFactoryBeanName = "myMessageProcessorFactory",
                messageRetrieverFactoryBeanName = "myMessageRetrieverFactory")
        public void listenToMessage(@Payload final String payload) {
            log.info("Obtained message: {}", payload);
            messagesProcessed.put(Integer.valueOf(payload), true);
            COUNT_DOWN_LATCH.countDown();
        }
    }
}
