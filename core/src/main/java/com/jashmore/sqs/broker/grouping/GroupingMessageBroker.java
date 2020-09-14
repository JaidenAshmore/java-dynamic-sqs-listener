package com.jashmore.sqs.broker.grouping;

import static com.jashmore.sqs.broker.grouping.GroupingMessageBrokerConstants.DEFAULT_CONCURRENCY_POLLING;

import com.jashmore.documentation.annotations.GuardedBy;
import com.jashmore.documentation.annotations.NotThreadSafe;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.util.properties.PropertyUtils;
import java.time.Duration;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * {@link MessageBroker} that concurrently processes messages and guarantees that two messages that are in the same message group are not processed at the
 * same time.
 *
 * <p>The concurrency rate will be recalculated after each message is successfully processed to determine if any more concurrent threads which will increase decrease the number of available threads for processing. For example,
 * if the current concurrency rate is 5 and when it recalculates the concurrency it is now 6 it will allow another thread to process messages. However,
 * if the rate of concurrency decreases it will wait until a certain number of messages finishes processing messages before requesting more messages.
 *
 * <p>Note that it may take a while for the concurrency rate to change based on whether all of the permits are currently being used, it will only recheck
 * the concurrency rate once another message is being used. The other way that the concurrency rate can be changed is if the request for a permit goes
 * over the desired length it will recalculate the concurrency and try again.
 *
 * <p>This is useful for FIFO queues as it will not allow for messages in the same message group to be concurrently
 *
 * @see GroupingMessageBrokerProperties for how to configure this broker
 */
@Slf4j
public class GroupingMessageBroker implements MessageBroker {
    private final GroupingMessageBrokerProperties properties;

    public GroupingMessageBroker(final GroupingMessageBrokerProperties properties) {
        this.properties = properties;
    }

    @Override
    public void processMessages(
        final ExecutorService messageProcessingExecutorService,
        final BooleanSupplier keepProcessingMessages,
        final Supplier<CompletableFuture<Message>> messageSupplier,
        final Function<Message, CompletableFuture<?>> messageProcessor
    ) {
        log.debug("Beginning processing of messages");

        final MessageBrokerExecutor executor = new MessageBrokerExecutor();

        executor.brokerMessages(messageProcessingExecutorService, keepProcessingMessages, messageSupplier, messageProcessor);

        executor.processCachedMessages(messageProcessingExecutorService, messageProcessor);

        log.debug("Finished processing of messages");
    }

    @NotThreadSafe
    private class MessageBrokerExecutor {
        /**
         * All of the message groups that are currently being processed.
         *
         * <p>
         */
        @GuardedBy("this")
        private final Set<String> messageGroupsCurrentlyProcessing = new HashSet<>();

        /**
         * All of the current messages that are being requested for processing.
         */
        @GuardedBy("this")
        private final Deque<CompletableFuture<Message>> messageRequests = new LinkedList<>();

        @GuardedBy("this")
        private final Map<String, Queue<Message>> messageGroupCache = new LinkedHashMap<>();

        private final AtomicBoolean listeningToMessage = new AtomicBoolean(false);

        private final AtomicInteger messageRequestId = new AtomicInteger();

        public void brokerMessages(
            final ExecutorService messageProcessingExecutorService,
            final BooleanSupplier keepProcessingMessages,
            final Supplier<CompletableFuture<Message>> messageSupplier,
            final Function<Message, CompletableFuture<?>> messageProcessor
        ) {
            log.debug("Beginning processing of messages");
            while (!Thread.currentThread().isInterrupted() && keepProcessingMessages.getAsBoolean()) {
                synchronized (this) {
                    if (getTotalCachedMessages() > properties.getConcurrencyLevel() * properties.getMaximumConcurrentMessageRetrieval()) {
                        log.error("I HAVE TOO MANY MESSAGES!!");
                    }
                    log.info(
                        "Current cache: {}",
                        messageGroupCache.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()))
                    );
                    log.trace("Starting iteration");

                    final boolean atLimit = processAsManyMessagesAsPossible(messageProcessingExecutorService, messageProcessor);

                    if (!atLimit) {
                        requestMoreMessages(messageSupplier);
                        processAsManyMessagesAsPossible(messageProcessingExecutorService, messageProcessor);
                    }

                    // The above could have created more messages that we can now process

                    try {
                        log.trace("Waiting for thread");
                        this.wait(); // TODO: Put the timeout here
                    } catch (final InterruptedException interruptedException) {
                        break;
                    }
                    log.trace("Next process");
                }
            }

            log.debug("Finished");
        }

        public void processCachedMessages(
            final ExecutorService messageProcessingExecutorService,
            final Function<Message, CompletableFuture<?>> messageProcessor
        ) {
            log.trace("Processing extra messages: {}", getTotalCachedMessages());
            try {
                while (true) {
                    synchronized (this) {
                        log.debug("Messages left: {} requests: {}", messageGroupCache.size(), messageRequests.size());
                        if (messageGroupCache.isEmpty() && messageRequests.isEmpty()) {
                            break;
                        }

                        processAsManyMessagesAsPossible(messageProcessingExecutorService, messageProcessor);

                        addMessageRetrievalListenerToNextMessage();

                        try {
                            log.trace("Waiting for thread");
                            this.wait();
                        } catch (final InterruptedException interruptedException) {
                            //break;
                        }
                    }
                }
                log.debug("Finished processing extra messages");
            } catch (RuntimeException e) {
                log.error("Error", e);
            }
        }

        private synchronized void requestMoreMessages(final Supplier<CompletableFuture<Message>> messageSupplier) {
            int desiredMessageRetrievalConcurrency = properties.getMaximumConcurrentMessageRetrieval();
            while (messageRequests.size() < desiredMessageRetrievalConcurrency) {
                log.trace("Requesting message");
                messageRequests.addLast(messageSupplier.get());

                addMessageRetrievalListenerToNextMessage();
            }
        }

        private synchronized void addMessageRetrievalListenerToNextMessage() {
            while (!messageRequests.isEmpty() && listeningToMessage.compareAndSet(false, true)) {
                log.trace("Adding then accept");
                final int messageId = messageRequestId.incrementAndGet();

                messageRequests
                    .getFirst()
                    .thenAccept(
                        message -> {
                            synchronized (this) {
                                final String messageGroupKey = properties.groupMessage(message);
                                log.info("{} Message downloaded: {}, value: {}", messageId, messageGroupKey, message.body());
                                if (!messageGroupCache.containsKey(messageGroupKey)) {
                                    messageGroupCache.put(messageGroupKey, new LinkedList<>());
                                }
                                messageGroupCache.get(messageGroupKey).add(message);
                                messageRequests.remove();
                                listeningToMessage.set(false);
                                this.notifyAll();
                            }
                        }
                    );
            }
        }

        private synchronized boolean processAsManyMessagesAsPossible(
            final ExecutorService messageProcessingExecutorService,
            final Function<Message, CompletableFuture<?>> messageProcessor
        ) {
            final int concurrencyLevel = getConcurrencyLevel();
            while (concurrencyLevel > messageGroupsCurrentlyProcessing.size()) {
                final Optional<Map.Entry<String, Queue<Message>>> optionalMessageGroup = messageGroupCache
                    .entrySet()
                    .stream()
                    .filter(entry -> !messageGroupsCurrentlyProcessing.contains(entry.getKey()))
                    .findFirst();

                if (!optionalMessageGroup.isPresent()) {
                    break;
                }

                final Map.Entry<String, Queue<Message>> availableMessageGroup = optionalMessageGroup.get();
                final String messageGroupKey = availableMessageGroup.getKey();
                final Queue<Message> messageGroupCachedMessages = availableMessageGroup.getValue();
                final Message nextMessage = messageGroupCachedMessages.poll();
                if (messageGroupCachedMessages.isEmpty()) {
                    messageGroupCache.remove(messageGroupKey);
                }

                log.trace("Found group: {}", messageGroupKey);

                messageGroupsCurrentlyProcessing.add(messageGroupKey);

                CompletableFuture
                    .runAsync(() -> messageProcessor.apply(nextMessage), messageProcessingExecutorService)
                    .whenComplete(
                        (ignoredResult, throwable) -> {
                            synchronized (this) {
                                if (throwable != null && !(throwable.getCause() instanceof CancellationException)) {
                                    log.error("Error processing message, all cached messages will be removed", throwable.getCause());
                                    messageGroupCache.remove(messageGroupKey);
                                }
                                messageGroupsCurrentlyProcessing.remove(messageGroupKey);
                                log.trace("Message finished processing: {}", messageGroupKey);
                                this.notifyAll();
                            }
                        }
                    );
            }

            return concurrencyLevel == messageGroupsCurrentlyProcessing.size();
        }

        private synchronized int getTotalCachedMessages() {
            return messageGroupCache.values().stream().mapToInt(Queue::size).sum();
        }
    }

    /**
     * Determine the concurrency level safely, returning zero if there was an error or the value was negative.
     *
     * @return the expected concurrency level
     */
    private int getConcurrencyLevel() {
        return PropertyUtils.safelyGetPositiveOrZeroIntegerValue("concurrencyLevel", properties::getConcurrencyLevel, 0);
    }

    /**
     * Safely get the number of milliseconds that should wait to get a permit for creating a new thread.
     *
     * @return the duration to wait for an available permit
     * @see GroupingMessageBrokerProperties#getConcurrencyPollingRate() for more information
     */
    private Duration getConcurrencyPollingDuration() {
        final Duration pollingRate = properties.getConcurrencyPollingRate();
        if (pollingRate != null && !pollingRate.isNegative()) {
            return pollingRate;
        }

        return DEFAULT_CONCURRENCY_POLLING;
    }
}
