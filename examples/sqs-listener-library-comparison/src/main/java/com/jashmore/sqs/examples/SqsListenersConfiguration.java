package com.jashmore.sqs.examples;

import static com.jashmore.sqs.examples.ExampleConstants.NUMBER_OF_MESSAGES;
import static com.jashmore.sqs.examples.ExampleConstants.QUEUE_TO_TEST;
import static com.jashmore.sqs.examples.Queues.JMS_10_QUEUE_NAME;
import static com.jashmore.sqs.examples.Queues.JMS_30_QUEUE_NAME;
import static com.jashmore.sqs.examples.Queues.PREFETCHING_10_QUEUE_NAME;
import static com.jashmore.sqs.examples.Queues.PREFETCHING_30_QUEUE_NAME;
import static com.jashmore.sqs.examples.Queues.QUEUE_LISTENER_10_QUEUE_NAME;
import static com.jashmore.sqs.examples.Queues.QUEUE_LISTENER_30_QUEUE_NAME;
import static com.jashmore.sqs.examples.Queues.SPRING_CLOUD_QUEUE_NAME;
import static java.util.stream.Collectors.toSet;

import akka.http.scaladsl.Http;
import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.jashmore.sqs.examples.latency.LatencyAppliedAmazonSqsAsync;
import com.jashmore.sqs.examples.latency.LatencyAppliedSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import lombok.extern.slf4j.Slf4j;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.springframework.cloud.aws.messaging.config.annotation.EnableSqs;
import org.springframework.cloud.aws.messaging.listener.QueueMessageHandler;
import org.springframework.cloud.aws.messaging.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.support.destination.DynamicDestinationResolver;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import javax.jms.Session;

/**
 * Configuration for each of the types of SQS Listeners that are available, as well as setting up the messages to be
 * processed.
 */
@EnableSqs
@EnableJms
@Slf4j
@Configuration
public class SqsListenersConfiguration {
    /**
     * Builds a in memory ElasticMQ SQS Server.
     *
     * @return sqs server
     */
    @Bean
    public SQSRestServer sqsRestServer() {
        log.info("Starting Local ElasticMQ SQS Server");
        return SQSRestServerBuilder
                .withInterface("localhost")
                .withDynamicPort()
                .start();
    }

    /**
     * Creates a SQS Client for the Java Dynamic SQS Listener Library with Latency inbuilt.
     *
     * @param sqsRestServer the sqs server
     * @return client for communicating with the local SQS
     * @throws Exception if there was an error
     */
    @Bean
    public SqsAsyncClient sqsAsyncClient(SQSRestServer sqsRestServer) throws Exception {
        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();
        final LocalSqsAsyncClient localSqsAsyncClient = new LocalSqsAsyncClient(SqsQueuesConfig
                .builder()
                .sqsServerUrl("http://localhost:" + serverBinding.localAddress().getPort())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName(JMS_10_QUEUE_NAME).build())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName(JMS_30_QUEUE_NAME).build())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName(SPRING_CLOUD_QUEUE_NAME).build())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName(PREFETCHING_10_QUEUE_NAME).build())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName(PREFETCHING_30_QUEUE_NAME).build())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName(QUEUE_LISTENER_10_QUEUE_NAME).build())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName(QUEUE_LISTENER_30_QUEUE_NAME).build())
                .build());

        localSqsAsyncClient.buildQueues();

        sendMessagesToQueue(localSqsAsyncClient);

        return new LatencyAppliedSqsAsyncClient(localSqsAsyncClient);
    }

    /**
     * Creates a SQS Client for the Spring Cloud and JMS SQS Listener Libraries.
     *
     * @param sqsRestServer the sqs server
     * @param ignored       depend on the client as that is what actually builds the queue and places the messages on it
     * @return client for communicating with the local SQS
     */
    @Bean
    public AmazonSQSAsync amazonSqs(SQSRestServer sqsRestServer, SqsAsyncClient ignored) {
        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();

        return new LatencyAppliedAmazonSqsAsync(AmazonSQSAsyncClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://localhost:" + serverBinding.localAddress().getPort(), "localstack")
                )
                .withCredentials(new AWSStaticCredentialsProvider(new com.amazonaws.auth.BasicAWSCredentials("X", "X")))
                .build());
    }

    // Spring Cloud AWS Beans

    /**
     * Container used by the Spring Cloud SQS Listener.
     *
     * <p>This is needing to be created to set the default max messages otherwise it will default to 3 and throw an exception if there are more than 3 messages
     * on the queue...so dumb. See <a href="https://github.com/spring-cloud/spring-cloud-aws/issues/373">#373</a> for more details about this bug.
     *
     * @param amazonSqs           the sqs client
     * @param queueMessageHandler the handler for processing messages
     * @return the container for this library
     */
    @Bean
    public SimpleMessageListenerContainer simpleMessageListenerContainer(AmazonSQSAsync amazonSqs, QueueMessageHandler queueMessageHandler) {
        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setMaxNumberOfMessages(10);
        container.setAmazonSqs(amazonSqs);
        container.setMessageHandler(queueMessageHandler);
        return container;
    }

    // JMS Beans

    /**
     * Set up for JMS to set the number of messages to prefetch.
     *
     * @return the configuration for JMS
     */
    @Bean
    public ProviderConfiguration providerConfiguration() {
        final ProviderConfiguration providerConfiguration = new ProviderConfiguration();
        // This setting sets how many messages a thread can prefetch to place in it's own queue for processing.
        // Note that this means that if the JmsListener is set to have concurrency 5 and therefore 5 threads are
        // all processing messages than the total number of messages that would be in the queue are
        // 5 * numberOfMessagesToPrefetch
        providerConfiguration.setNumberOfMessagesToPrefetch(5);
        return providerConfiguration;
    }

    /**
     * Create factory for attaching to the SQS Queues.
     *
     * @param amazonSqs             the sqs client
     * @param providerConfiguration configuration for this provided
     * @return the JMS container factory used
     */
    @Bean
    public JmsListenerContainerFactory<DefaultMessageListenerContainer> jmsListenerContainerFactory(final AmazonSQSAsync amazonSqs,
                                                                                                    final ProviderConfiguration providerConfiguration) {
        final DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(new SQSConnectionFactory(providerConfiguration, amazonSqs));
        factory.setDestinationResolver(new DynamicDestinationResolver());
        // This sets the default concurrency for each listener but it can be overridden by your JmsListener
        factory.setConcurrency("10");
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        return factory;
    }

    private void sendMessagesToQueue(final SqsAsyncClient sqsAsyncClient) throws ExecutionException, InterruptedException {
        final String queueUrl = sqsAsyncClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(QUEUE_TO_TEST).build()).get().queueUrl();
        for (int i = 0; i < NUMBER_OF_MESSAGES; i = i + 10) {
            final int base = i;

            final SendMessageBatchRequest.Builder batchRequestBuilder = SendMessageBatchRequest.builder().queueUrl(queueUrl);
            batchRequestBuilder.entries(IntStream.range(0, 10)
                    .mapToObj(index -> {
                        final String messageId = "" + (base + index);
                        return SendMessageBatchRequestEntry.builder()
                                .id(messageId)
                                .messageBody(messageId)
                                .build();
                    })
                    .collect(toSet()));
            log.info("Put 10 messages onto queue");
            try {
                sqsAsyncClient.sendMessageBatch(batchRequestBuilder.build()).get();
            } catch (InterruptedException | ExecutionException exception) {
                throw new RuntimeException(exception);
            }
        }
    }
}
