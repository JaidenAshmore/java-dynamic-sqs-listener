package com.jashmore.sqs.broker.grouping;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBroker;
import com.jashmore.sqs.broker.concurrent.ConcurrentMessageBrokerProperties;
import com.jashmore.sqs.util.properties.PropertyUtils;
import java.time.Duration;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

/**
 * {@link MessageBroker} that concurrently processes messages that are grouped by a certain criteria, like the
 * {@link MessageSystemAttributeName#MESSAGE_GROUP_ID} of a FIFO queue.
 *
 * <p>The following properties of a {@link GroupingMessageBroker} are:
 * <ul>
 *     <li>no two messages in the same message group can be processed at the same time</li>
 *     <li>each message in the message group must be processed in order that they are received from SQS</li>
 *     <li>there is a limit for the number of messages to process at the same time but can change during processing</li>
 *     <li>will strive to have as many messages processing as possible</li>
 *     <li>attempts to process a new message as soon as another has finished to maintain concurrency</li>
 *     <li>when a message is retrieved it should request another one straight away</li>
 *     <li>on message processing failure the rest of the messages in the message group can be removed when configured to</li>
 * </ul>
 *
 * <p>The concurrency rate will be recalculated when more messages are obtained or a message has finished processing. For example, if the current concurrency
 * rate is 5 and when it recalculates the concurrency it is now 6 it will allow another thread to process messages. However,
 * if the rate of concurrency decreases it will wait until a certain number of messages finish processing before requesting more messages. Due to this it
 * may take a slight delay for the concurrency rate to change.
 *
 * @see GroupingMessageBrokerProperties for how to configure this broker
 */
@Slf4j
public class GroupingMessageBroker implements MessageBroker {
    private final GroupingMessageBrokerProperties properties;
    private final ConcurrentMessageBroker concurrentMessageBroker;
    private final ReentrantLock reentrantLock = new ReentrantLock();

    /**
     * Contains all of the messages that have been received but cannot be processed because there is not a thread to take it or there is currently already
     * a message in the same message group being processed.
     */
    private final Map<String, Queue<Message>> internalMessageCache = new LinkedHashMap<>();

    /**
     * All of the requests for messages.
     *
     * <p>This is needing to be stored to make sure that we cancel them when we are stopping this broker.
     */
    private final Set<CompletableFuture<Message>> actualMessageRequests = new HashSet<>();

    /**
     * Requests for messages by the {@link #concurrentMessageBroker} which indicates that another thread can begin processing a new message.
     */
    private final Deque<CompletableFuture<Message>> delegateBrokerRequestsForMessages = new LinkedList<>();

    /**
     * Stores all of the message groups that are currently running so that two messages with the same group are not executed at the same time.
     */
    private final Set<String> messageGroupsCurrentlyProcessing = new HashSet<>();

    /**
     * Stores a map of the messages that failed to process from the message's message group ID to the time that the message failed to be processed.
     */
    private final Map<String, Long> failingMessages = new HashMap<>();

    public GroupingMessageBroker(final GroupingMessageBrokerProperties properties) {
        this.properties = properties;
        this.concurrentMessageBroker =
            new ConcurrentMessageBroker(
                new ConcurrentMessageBrokerProperties() {

                    @Override
                    public @PositiveOrZero int getConcurrencyLevel() {
                        return properties.getConcurrencyLevel();
                    }

                    @Override
                    public @Nullable @Positive Duration getConcurrencyPollingRate() {
                        return properties.getConcurrencyPollingRate();
                    }

                    @Override
                    public @Nullable @PositiveOrZero Duration getErrorBackoffTime() {
                        return properties.getErrorBackoffTime();
                    }
                }
            );
    }

    @Override
    public void processMessages(
        final ExecutorService messageProcessingExecutorService,
        final BooleanSupplier keepProcessingMessages,
        final Supplier<CompletableFuture<Message>> messageSupplier,
        final Function<Message, CompletableFuture<?>> messageProcessor
    )
        throws InterruptedException {
        log.debug("Beginning processing of messages");
        normalProcessingOfMessage(messageProcessingExecutorService, keepProcessingMessages, messageSupplier, messageProcessor);
        cancelAllRequestsForMessages();
        log.debug("Ending processing of messages");

        if (properties.processCachedMessagesOnShutdown()) {
            log.debug("Beginning processing of internally cached messages");
            processInternallyCachedMessages(messageProcessingExecutorService, messageSupplier, messageProcessor);
            log.debug("Ending processing of internally cached messages");
        }
    }

    private void normalProcessingOfMessage(
        final ExecutorService messageProcessingExecutorService,
        final BooleanSupplier keepProcessingMessages,
        final Supplier<CompletableFuture<Message>> messageSupplier,
        final Function<Message, CompletableFuture<?>> messageProcessor
    ) {
        try {
            concurrentMessageBroker.processMessages(
                messageProcessingExecutorService,
                keepProcessingMessages,
                wrapMessageSupplier(messageSupplier, true),
                wrapMessageProcessor(messageProcessor)
            );
        } catch (InterruptedException interruptedException) {
            // do nothing
        }
    }

    private void processInternallyCachedMessages(
        final ExecutorService messageProcessingExecutorService,
        final Supplier<CompletableFuture<Message>> messageSupplier,
        final Function<Message, CompletableFuture<?>> messageProcessor
    )
        throws InterruptedException {
        concurrentMessageBroker.processMessages(
            messageProcessingExecutorService,
            () -> !internalMessageCache.isEmpty(),
            wrapMessageSupplier(messageSupplier, false),
            wrapMessageProcessor(messageProcessor)
        );
    }

    private void cancelAllRequestsForMessages() {
        reentrantLock.lock();
        try {
            actualMessageRequests.forEach(future -> future.cancel(true));
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * Wraps the actual message processor with logic to handle message groups that are current processing as well as handling any failing messages.
     *
     * <p>It will attempt to run more message processing if possible.
     *
     * @param messageProcessor the actual message processor to process the message
     * @return a wrapped message processor
     */
    private Function<Message, CompletableFuture<?>> wrapMessageProcessor(final Function<Message, CompletableFuture<?>> messageProcessor) {
        return message -> {
            final String messageGroupKey = properties.messageGroupingFunction().apply(message);
            return messageProcessor
                .apply(message)
                .handle(
                    (ignored, throwable) -> {
                        reentrantLock.lock();
                        try {
                            if (throwable != null) {
                                final Throwable actualThrowable;
                                if (throwable instanceof CompletionException) {
                                    actualThrowable = throwable.getCause();
                                } else {
                                    actualThrowable = throwable;
                                }
                                log.error("Error processing message", actualThrowable);
                                if (properties.purgeExtraMessagesInGroupOnError()) {
                                    failingMessages.put(messageGroupKey, System.currentTimeMillis());
                                    internalMessageCache.remove(messageGroupKey);
                                }
                            }
                            messageGroupsCurrentlyProcessing.remove(properties.messageGroupingFunction().apply(message));

                            tryProcessAnotherMessage();
                        } finally {
                            reentrantLock.unlock();
                        }
                        return null;
                    }
                );
        };
    }

    /**
     * Wraps the message supplier with logic to internally cache messages.
     *
     * @param messageSupplier the actual message supplier
     * @param withMessageRetrieval whether requesting a message should actually call out to the delegate for the message
     * @return the wrapped message supplier
     */
    private Supplier<CompletableFuture<Message>> wrapMessageSupplier(
        final Supplier<CompletableFuture<Message>> messageSupplier,
        final boolean withMessageRetrieval
    ) {
        return () -> {
            reentrantLock.lock();
            try {
                if (delegateBrokerRequestsForMessages.isEmpty()) {
                    final Optional<Message> optionalCachedMessage = getInternalCachedMessageAvailableForProcessing();
                    if (optionalCachedMessage.isPresent()) {
                        final Message message = optionalCachedMessage.get();
                        final String messageGroupKey = properties.messageGroupingFunction().apply(message);
                        messageGroupsCurrentlyProcessing.add(messageGroupKey);
                        return CompletableFuture.completedFuture(message);
                    }
                }

                final CompletableFuture<Message> messageRequest = new CompletableFuture<>();

                delegateBrokerRequestsForMessages.addLast(messageRequest);

                if (withMessageRetrieval) {
                    performMessageRetrieval(messageSupplier);
                }

                return messageRequest;
            } finally {
                reentrantLock.unlock();
            }
        };
    }

    /**
     * Actually attempt to get a new message depending on whether too many messages will be cached if this is successfully retrieved.
     *
     * @param messageSupplier the delegate message supplier
     */
    private void performMessageRetrieval(final Supplier<CompletableFuture<Message>> messageSupplier) {
        reentrantLock.lock();
        try {
            if (internalMessageCache.size() + actualMessageRequests.size() < getMaximumNumberOfCachedMessageGroups()) {
                final CompletableFuture<Message> messageRetrievalFuture = messageSupplier.get();
                actualMessageRequests.add(messageRetrievalFuture);

                messageRetrievalFuture.thenAccept(
                    message -> {
                        final String messageGroupKey = properties.messageGroupingFunction().apply(message);
                        reentrantLock.lock();
                        try {
                            final boolean messageWithSameGroupFailedInShortPeriod = failingMessages
                                .entrySet()
                                .stream()
                                .filter(entry -> System.currentTimeMillis() - entry.getValue() < Duration.ofSeconds(1).toMillis())
                                .anyMatch(entry -> entry.getKey().equals(messageGroupKey));

                            actualMessageRequests.remove(messageRetrievalFuture);
                            if (!messageWithSameGroupFailedInShortPeriod) {
                                failingMessages.remove(messageGroupKey);
                                internalMessageCache.putIfAbsent(messageGroupKey, new LinkedList<>());
                                internalMessageCache.get(messageGroupKey).add(message);
                            }

                            tryProcessAnotherMessage();

                            performMessageRetrieval(messageSupplier);
                        } finally {
                            reentrantLock.unlock();
                        }
                    }
                );
            }
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * Determine if there is an internally cached message that can begin to be processed.
     *
     * @return the optional message to process
     */
    private Optional<Message> getInternalCachedMessageAvailableForProcessing() {
        final Optional<Map.Entry<String, Queue<Message>>> optionalMessageGroupValue = internalMessageCache
            .entrySet()
            .stream()
            .filter(entry -> !messageGroupsCurrentlyProcessing.contains(entry.getKey()))
            .findFirst();

        if (!optionalMessageGroupValue.isPresent()) {
            return Optional.empty();
        }

        final Map.Entry<String, Queue<Message>> optionalMessageGroup = optionalMessageGroupValue.get();

        final String messageGroupKey = optionalMessageGroup.getKey();
        final Queue<Message> messageQueueCache = optionalMessageGroup.getValue();
        final Message message = messageQueueCache.remove();
        if (messageQueueCache.isEmpty()) {
            internalMessageCache.remove(messageGroupKey);
        }

        return Optional.of(message);
    }

    /**
     * Request another message to be processed if possible.
     */
    private void tryProcessAnotherMessage() {
        reentrantLock.lock();
        try {
            if (delegateBrokerRequestsForMessages.isEmpty()) {
                return;
            }

            getInternalCachedMessageAvailableForProcessing()
                .ifPresent(
                    messageToProcess -> {
                        final String messageToProcessGroupKey = properties.messageGroupingFunction().apply(messageToProcess);
                        log.trace("Processing message for group: {}", messageToProcessGroupKey);
                        messageGroupsCurrentlyProcessing.add(messageToProcessGroupKey);
                        final CompletableFuture<Message> future = delegateBrokerRequestsForMessages.removeFirst();
                        future.complete(messageToProcess);
                    }
                );
        } finally {
            reentrantLock.unlock();
        }
    }

    private int getMaximumNumberOfCachedMessageGroups() {
        return PropertyUtils.safelyGetPositiveIntegerValue(
            "maximumNumberOfCachedMessageGroups",
            properties::getMaximumNumberOfCachedMessageGroups,
            1
        );
    }
}
