package com.jashmore.sqs.examples;

import static com.jashmore.sqs.aws.AwsConstants.MAX_NUMBER_OF_MESSAGES_IN_BATCH;
import static java.util.stream.Collectors.toSet;

import akka.http.scaladsl.Http;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.CoreArgumentResolverService;
import com.jashmore.sqs.argument.messageid.MessageId;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.properties.CachingConcurrentMessageBrokerProperties;
import com.jashmore.sqs.broker.concurrent.properties.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.processor.DefaultMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.processor.resolver.MessageResolver;
import com.jashmore.sqs.processor.resolver.batching.BatchingMessageResolver;
import com.jashmore.sqs.processor.resolver.batching.StaticBatchingMessageResolverProperties;
import com.jashmore.sqs.retriever.AsyncMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingMessageRetriever;
import com.jashmore.sqs.retriever.prefetch.PrefetchingProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.validation.constraints.Min;

/**
 * This example shows the core framework being used to processing messages place onto the queue with a dynamic level of concurrency via the
 * {@link ConcurrentMessageBroker}. The rate of concurrency is randomly changed every {@link #CONCURRENCY_LEVEL_PERIOD_IN_MS} to a new value between 0
 * and {@link #CONCURRENCY_LEVEL_LIMIT}.
 *
 * <p>This example will also show how the performance of the message processing can be improved by prefetching messages via the
 * {@link PrefetchingMessageRetriever}.
 *
 * <p>While this is running you should see the messages being processed and the number of messages that are concurrently being processed. This will highlight
 * how the concurrency can change during the running of the application.
 */
@Slf4j
public class ConcurrentBrokerExample {
    private static final int CONCURRENCY_LEVEL_PERIOD_IN_MS = 5000;
    private static final int CONCURRENCY_LEVEL_LIMIT = 10;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String QUEUE_NAME = "my_queue";

    /**
     * Example that will continue to place messages on the message queue with a message listener consuming them.
     *
     * @param args unused args
     * @throws Exception if there was a problem running the program
     */
    public static void main(final String[] args) throws Exception {
        // Sets up the SQS that will be used
        final SqsAsyncClient sqsAsyncClient = startElasticMqServer();
        final String queueUrl = sqsAsyncClient.createQueue((request) -> request.queueName(QUEUE_NAME).build()).get().queueUrl();
        final QueueProperties queueProperties = QueueProperties
                .builder()
                .queueUrl(queueUrl)
                .build();

        final ExecutorService executorService = Executors.newCachedThreadPool();

        // Creates the class that will actually perform the logic for retrieving messages from the queue
        final AsyncMessageRetriever messageRetriever = new PrefetchingMessageRetriever(
                sqsAsyncClient,
                queueProperties,
                PrefetchingProperties
                        .builder()
                        .desiredMinPrefetchedMessages(10)
                        .maxPrefetchedMessages(20)
                        .maxWaitTimeInSecondsToObtainMessagesFromServer(10)
                        .build(),
                executorService
        );

        // As this retrieves messages asynchronously we need to start the background thread
        messageRetriever.start();

        // Creates the class that will deal with taking messages and getting them processed by the message consumer
        final MessageConsumer messageConsumer = new MessageConsumer();
        final Method messageReceivedMethod = MessageConsumer.class.getMethod("method", Request.class, String.class);
        final MessageResolver messageResolver = new BatchingMessageResolver(queueProperties, sqsAsyncClient, StaticBatchingMessageResolverProperties.builder()
                .bufferingSizeLimit(MAX_NUMBER_OF_MESSAGES_IN_BATCH)
                .bufferingTimeInMs(5000)
                .build());
        final MessageProcessor messageProcessor = new DefaultMessageProcessor(
                argumentResolverService(sqsAsyncClient),
                queueProperties,
                messageResolver,
                messageReceivedMethod,
                messageConsumer
        );

        // Build a container that will glue the retrieval and processing of the messages over multiple threads concurrently
        final ConcurrentMessageBroker concurrentMessageBroker = new ConcurrentMessageBroker(
                messageRetriever,
                messageProcessor,
                executorService,
                // Represents a concurrent implementation that will fluctuate between 0 and 10 threads all processing messages
                new CachingConcurrentMessageBrokerProperties(10000, new ConcurrentMessageBrokerProperties() {
                    private final Random random = new Random(1);

                    @Override
                    public Integer getConcurrencyLevel() {
                        return random.nextInt(CONCURRENCY_LEVEL_LIMIT);
                    }

                    @Override
                    public @Min(0) Integer getPreferredConcurrencyPollingRateInMilliseconds() {
                        return CONCURRENCY_LEVEL_PERIOD_IN_MS;
                    }
                })
        );

        // When we start listening it will receive messages from SQS and pass them to the MessageConsumer for processing
        concurrentMessageBroker.start();

        // Create some producers of messages
        final Future<?> producerFuture = executorService.submit(new Producer(sqsAsyncClient, queueUrl));

        // Wait until the first producer is done, this should never resolve
        producerFuture.get();
    }

    /**
     * Runs a local Elastic MQ server that will act like the SQS queue for local testing.
     *
     * <p>This is useful as it means the users of this example don't need to worry about setting up any queue system them self.
     *
     * @return amazon sqs client for connecting the local queue
     */
    private static SqsAsyncClient startElasticMqServer() throws URISyntaxException {
        log.info("Starting Local ElasticMQ SQS Server");
        final SQSRestServer sqsRestServer = SQSRestServerBuilder
                .withInterface("localhost")
                .withDynamicPort()
                .start();

        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();

        return SqsAsyncClient.builder()
                .region(Region.of("elasticmq"))
                .endpointOverride(new URI("http://localhost:" + serverBinding.localAddress().getPort()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("accessKeyId", "secretAccessKey")))
                .build();
    }

    /**
     * Builds the {@link ArgumentResolverService} that will be used to parse the messages into arguments for the {@link MessageConsumer}.
     *
     * @param sqsAsyncClient the client to communicate with the SQS queue
     * @return the service to resolve arguments for the message consumer
     */
    private static ArgumentResolverService argumentResolverService(final SqsAsyncClient sqsAsyncClient) {
        final PayloadMapper payloadMapper = new JacksonPayloadMapper(OBJECT_MAPPER);
        return new CoreArgumentResolverService(payloadMapper, sqsAsyncClient);
    }

    /**
     * The thread that will be producing messages to place onto the queue.
     */
    @Slf4j
    @AllArgsConstructor
    private static class Producer implements Runnable {
        /**
         * Contains the number of messages that will be published from the producer at once.
         */
        private static final int NUMBER_OF_MESSAGES_FOR_PRODUCER = 10;

        private final SqsAsyncClient async;
        private final String queueUrl;

        @Override
        public void run() {
            final AtomicInteger count = new AtomicInteger(0);
            boolean shouldStop = false;
            while (!shouldStop) {
                try {
                    final SendMessageBatchRequest.Builder batchRequestBuilder = SendMessageBatchRequest.builder().queueUrl(queueUrl);
                    batchRequestBuilder.entries(IntStream.range(0, NUMBER_OF_MESSAGES_FOR_PRODUCER)
                            .mapToObj(index -> {
                                final String messageId = "" + index;
                                final Request request = new Request("key_" + count.getAndIncrement());
                                try {
                                    return SendMessageBatchRequestEntry.builder().id(messageId).messageBody(OBJECT_MAPPER.writeValueAsString(request)).build();
                                } catch (JsonProcessingException exception) {
                                    throw new RuntimeException(exception);
                                }
                            })
                            .collect(toSet()));
                    log.info("Put 10 messages onto queue");
                    async.sendMessageBatch(batchRequestBuilder.build());
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
         *
         * @param request   the payload of the message
         * @param messageId the SQS message ID of this message
         * @throws InterruptedException if the thread was interrupted while sleeping
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
