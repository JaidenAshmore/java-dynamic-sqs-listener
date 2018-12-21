package com.jashmore.sqs.examples;

import akka.http.scaladsl.Http;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DefaultArgumentResolverService;
import com.jashmore.sqs.argument.messageid.MessageId;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.CachingConcurrentMessageBrokerProperties;
import com.jashmore.sqs.broker.concurrent.properties.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.validation.constraints.Min;

@Slf4j
public class ConcurrentBrokerExample {
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String QUEUE_NAME = "my_queue";
    /**
     * Contains the number of messages that will be published from the producer at once.
     */
    private static final int NUMBER_OF_MESSAGES_FOR_REDUCER = 10;
    private static final PrefetchingProperties PREFETCHING_PROPERTIES = PrefetchingProperties
            .builder()
            .desiredMinPrefetchedMessages(40)
            .maxPrefetchedMessages(50)
            .maxWaitTimeInSecondsToObtainMessagesFromServer(10)
            .build();

    /**
     * Runs a local Elastic MQ server that will act like the SQS queue for local testing.
     *
     * <p>This is useful as it means the users of this example don't need to worry about setting up any queue system them self like
     * localstack.
     *
     * @return amazon sqs client for connecting the local queue
     */
    private static AmazonSQSAsync startElasticMqServer() {
        log.info("Starting Local ElasticMQ SQS Server");
        final SQSRestServer sqsRestServer = SQSRestServerBuilder
                .withInterface("localhost")
                .withDynamicPort()
                .start();

        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();

        return AmazonSQSAsyncClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:" + serverBinding.localAddress().getPort(), "elasticmq"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
                .build();
    }

    /**
     * Example that will continue to place messages on the message queue with a message listener consuming them.
     */
    public static void main(final String[] args) throws Exception {
        // Sets up the SQS that will be used
        final AmazonSQSAsync amazonSqsAsync = startElasticMqServer();
        amazonSqsAsync.createQueue(QUEUE_NAME);
        final String queueUrl = amazonSqsAsync.getQueueUrl(QUEUE_NAME).getQueueUrl();
        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(queueUrl)
                .build();

        // Creates the class that will actually perform the logic for retrieving messages from the queue
        final AsyncMessageRetriever messageRetriever = new PrefetchingMessageRetriever(
                amazonSqsAsync,
                queueProperties,
                PREFETCHING_PROPERTIES,
                EXECUTOR_SERVICE
        );

        // As this retrieves messages asynchronously we need to start the background thread
        messageRetriever.start();

        // Creates the class that will deal with taking messages and getting them processed by the message consumer
        final MessageConsumer messageConsumer = new MessageConsumer();
        final Method messageReceivedMethod = MessageConsumer.class.getMethod("method", Request.class, String.class);
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                argumentResolverService(amazonSqsAsync),
                queueProperties,
                amazonSqsAsync,
                messageReceivedMethod,
                messageConsumer
        );

        // Build a container that will glue the retrieval and processing of the messages over multiple threads concurrently
        final ConcurrentMessageBroker concurrentMessageBroker = new ConcurrentMessageBroker(
                messageRetriever,
                messageProcessor,
                EXECUTOR_SERVICE,
                // Represents a concurrent implementation that will fluctuate between 0 and 10 threads all processing messages
                new CachingConcurrentMessageBrokerProperties(10000, new ConcurrentMessageBrokerProperties() {
                    private final Random random = new Random(1);

                    @Override
                    public Integer getConcurrencyLevel() {
                        return random.nextInt(10);
                    }

                    @Override
                    public @Min(0) Integer getPreferredConcurrencyPollingRateInMilliseconds() {
                        return 5000;
                    }
                })
        );

        // When we start listening it will receive messages from SQS and pass them to the MessageConsumer for processing
        concurrentMessageBroker.start();

        // Create some producers of messages
        final Future<?> producerFuture = EXECUTOR_SERVICE.submit(new Producer(amazonSqsAsync, queueUrl, OBJECT_MAPPER));

        // Wait until the first producer is done, this should never resolve
        producerFuture.get();
    }

    /**
     * Builds the {@link ArgumentResolverService} that will be used to parse the messages into arguments for the {@link MessageConsumer}.
     *
     * @param amazonSqsAsync the client to communicate with the SQS queue
     * @return the service to resolve arguments for the message consumer
     */
    private static ArgumentResolverService argumentResolverService(final AmazonSQSAsync amazonSqsAsync) {
        final PayloadMapper payloadMapper = new JacksonPayloadMapper(OBJECT_MAPPER);
        return new DefaultArgumentResolverService(payloadMapper, amazonSqsAsync);
    }

    /**
     * The thread that will be producing messages to place onto the queue.
     */
    @Slf4j
    @AllArgsConstructor
    private static class Producer implements Runnable {
        private final AmazonSQSAsync async;
        private final String queueUrl;
        private final ObjectMapper objectMapper;

        @Override
        public void run() {
            int count = 0;
            boolean shouldStop = false;
            while (!shouldStop) {
                try {
                    final SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest(queueUrl);
                    for (int i = 0; i < NUMBER_OF_MESSAGES_FOR_REDUCER; ++i) {
                        final String messageId = "" + count;
                        final Request request = new Request("key_" + count);
                        sendMessageBatchRequest.withEntries(new SendMessageBatchRequestEntry(messageId, objectMapper.writeValueAsString(request)));
                        ++count;
                    }
                    log.info("Put 10 messages onto queue");
                    async.sendMessageBatch(sendMessageBatchRequest);
                    Thread.sleep(2000);
                } catch (final InterruptedException interruptedException) {
                    log.info("Producer Thread has been interrupted");
                    shouldStop = true;
                } catch (final Exception exception) {
                    throw new RuntimeException(exception);
                }
            }
        }
    }

    @Data
    @AllArgsConstructor
    private static class Request {
        private final String key;
    }

    @Slf4j
    @SuppressWarnings("WeakerAccess")
    public static class MessageConsumer {
        /**
         * Static variable that is used to show that the level of concurrency is being followed.
         *
         * <p>This is useful when the concurrency is dynamically changing we can see that we are in fact properly processing them with the correct
         * concurrency level.
         */
        private static AtomicInteger concurrentMessagesBeingProcessed = new AtomicInteger(0);

        /**
         * Random number generator for calculating the random time that a message will take to be processed.
         */
        private final Random processingTimeRandom = new Random();

        /**
         * Method that will consume the messages.
         */
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
