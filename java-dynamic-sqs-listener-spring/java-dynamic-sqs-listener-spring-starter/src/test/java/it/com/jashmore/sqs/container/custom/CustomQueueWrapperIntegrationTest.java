package it.com.jashmore.sqs.container.custom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.broker.concurrent.properties.StaticConcurrentMessageBrokerProperties;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.resolver.MessageResolver;
import com.jashmore.sqs.processor.resolver.individual.IndividualMessageResolver;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.StaticPrefetchingMessageRetrieverProperties;
import com.jashmore.sqs.spring.container.custom.CustomQueueListener;
import com.jashmore.sqs.spring.container.custom.MessageBrokerFactory;
import com.jashmore.sqs.spring.container.custom.MessageProcessorFactory;
import com.jashmore.sqs.spring.container.custom.MessageRetrieverFactory;
import com.jashmore.sqs.test.LocalSqsRule;
import com.jashmore.sqs.test.PurgeQueuesRule;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import it.com.jashmore.example.Application;
import lombok.extern.slf4j.Slf4j;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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

    @ClassRule
    public static final LocalSqsRule LOCAL_SQS_RULE = new LocalSqsRule(ImmutableList.of(
            SqsQueuesConfig.QueueConfig.builder().queueName("CustomQueueWrapperIntegrationTest").build()
    ));

    @Rule
    public final PurgeQueuesRule purgeQueuesRule = new PurgeQueuesRule(LOCAL_SQS_RULE.getLocalAmazonSqsAsync());

    @Configuration
    @SuppressWarnings( {"SpringJavaInjectionPointsAutowiringInspection", "CheckStyle"})
    public static class TestConfig {
        public static class MessageListener {
            @SuppressWarnings( {"CheckStyle", "unused"})
            @CustomQueueListener(queue = "CustomQueueWrapperIntegrationTest",
                    messageBrokerFactoryBeanName = "myMessageBrokerFactory",
                    messageProcessorFactoryBeanName = "myMessageProcessorFactory",
                    messageRetrieverFactoryBeanName = "myMessageRetrieverFactory")
            public void listenToMessage(@Payload final String payload) {
                log.info("Obtained message: {}", payload);
                messagesProcessed.put(Integer.valueOf(payload.replace("message: ", "")), true);
                COUNT_DOWN_LATCH.countDown();
            }
        }

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return LOCAL_SQS_RULE.getLocalAmazonSqsAsync();
        }

        @Bean
        public MessageListener messageListener() {
            return new MessageListener();
        }

        @Bean
        public MessageRetrieverFactory myMessageRetrieverFactory(final SqsAsyncClient sqsAsyncClient) {
            return (queueProperties) -> {
                final StaticPrefetchingMessageRetrieverProperties staticPrefetchingMessageRetrieverProperties = StaticPrefetchingMessageRetrieverProperties
                        .builder()
                        .maxPrefetchedMessages(10)
                        .desiredMinPrefetchedMessages(1)
                        .maxWaitTimeInSecondsToObtainMessagesFromServer(5)
                        .visibilityTimeoutForMessagesInSeconds(30)
                        .errorBackoffTimeInMilliseconds(10)
                        .build();
                return new PrefetchingMessageRetriever(sqsAsyncClient, queueProperties, staticPrefetchingMessageRetrieverProperties, Executors.newCachedThreadPool());
            };
        }

        @Bean
        public MessageProcessorFactory myMessageProcessorFactory(final ArgumentResolverService argumentResolverService,
                                                                 final SqsAsyncClient sqsAsyncClient) {
            return (queueProperties, bean, method) -> {
                final MessageResolver messageResolver = new IndividualMessageResolver(queueProperties, sqsAsyncClient);
                return new DefaultMessageProcessor(argumentResolverService, queueProperties, messageResolver, method, bean);
            };
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
        IntStream.range(0, NUMBER_OF_MESSAGES_TO_SEND)
                .forEach(i -> {
                    log.info("Sending message: " + i);
                    LOCAL_SQS_RULE.getLocalAmazonSqsAsync().sendMessageToLocalQueue("CustomQueueWrapperIntegrationTest", "message: " + i);
                });

        // act
        COUNT_DOWN_LATCH.await(60, TimeUnit.SECONDS);

        // assert
        final List<Integer> processed = new ArrayList<>(messagesProcessed.keySet());
        Collections.sort(processed);
        assertThat(processed).hasSize(100);
    }
}
