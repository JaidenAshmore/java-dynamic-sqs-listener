package com.jashmore.sqs.decorator;

import com.jashmore.documentation.annotations.ThreadSafe;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.util.collections.CollectionUtils;
import com.jashmore.sqs.util.thread.ThreadUtils;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * {@link MessageProcessingDecorator} that will continually extend the visibility of the message while it is being processed.
 *
 * <p>No effort is made to guarantee that a message is successfully extended and therefore if the request fails or partially fails (some messages are not
 * extended) it will not re-attempt to extend them and just assume that they passed. Therefore if you desire a higher certainty that the visibility
 * extension will succeed you can configure the {@link AutoVisibilityExtenderMessageProcessingDecoratorProperties#bufferDuration()} to be a higher value.
 * For example, you could have the {@link AutoVisibilityExtenderMessageProcessingDecoratorProperties#visibilityTimeout()} to be 30 seconds but the
 * {@link AutoVisibilityExtenderMessageProcessingDecoratorProperties#bufferDuration()} to be 20 seconds and therefore you will have 3 attempts to successfully
 * extend the message.
 *
 * <p>Note that this only works with synchronous implementations of the message listener, e.g. functions that are executed using the
 * {@link com.jashmore.sqs.processor.LambdaMessageProcessor} or the {@link com.jashmore.sqs.processor.CoreMessageProcessor} where the function does not
 * return a {@link CompletableFuture}. This is because it is not easy to interrupt the processing of a message if it has been placed onto a different
 * thread to process.
 *
 * <p>This {@link MessageProcessingDecorator} is thread safe and will work safely when multiple messages are all being processed at once.
 *
 * @see AutoVisibilityExtenderMessageProcessingDecoratorProperties for configuration options
 */
@ThreadSafe
public class AutoVisibilityExtenderMessageProcessingDecorator implements MessageProcessingDecorator {

    private static final Logger log = LoggerFactory.getLogger(AutoVisibilityExtenderMessageProcessingDecorator.class);

    private final SqsAsyncClient sqsAsyncClient;
    private final QueueProperties queueProperties;
    private final AutoVisibilityExtenderMessageProcessingDecoratorProperties decoratorProperties;
    private final Map<Message, MessageProcessingState> currentMessagesProcessing;
    private final Object waitingLock = new Object();

    public AutoVisibilityExtenderMessageProcessingDecorator(
        final SqsAsyncClient sqsAsyncClient,
        final QueueProperties queueProperties,
        final AutoVisibilityExtenderMessageProcessingDecoratorProperties decoratorProperties
    ) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.queueProperties = queueProperties;
        this.decoratorProperties = decoratorProperties;

        this.currentMessagesProcessing = new HashMap<>();
    }

    @Override
    public void onPreMessageProcessing(final MessageProcessingContext context, final Message message) {
        synchronized (waitingLock) {
            final Instant timeNow = Instant.now();
            log.debug("Registering message {} with visibility auto extender", message.messageId());
            currentMessagesProcessing.put(
                message,
                ImmutableMessageProcessingState
                    .builder()
                    .thread(Thread.currentThread())
                    .startTime(timeNow)
                    .nextVisibilityExtensionTime(nextExtensionTime(timeNow, message, decoratorProperties.bufferDuration()))
                    .build()
            );

            if (currentMessagesProcessing.size() == 1) {
                CompletableFuture
                    .runAsync(
                        this::performBackgroundThread,
                        Executors.newSingleThreadExecutor(
                            ThreadUtils.singleNamedThreadFactory(context.getListenerIdentifier() + "-auto-visibility-extender")
                        )
                    )
                    .whenComplete(
                        (ignored, throwable) -> {
                            if (throwable != null) {
                                log.error("Unexpected error with visibility timeout extender", throwable);
                            }
                        }
                    );
            }

            // We need to notify the background thread to recalculate the updated time in case it has configured this message to have a smaller visibility
            // timeout then the current wait time
            waitingLock.notify();
        }
    }

    @Override
    public void onMessageProcessingThreadComplete(final MessageProcessingContext context, final Message message) {
        removeMessageFromAutoVisibilityExtender(message);
    }

    @Override
    public void onMessageResolve(MessageProcessingContext context, Message message) {
        // Needed in case the message listener is manually acknowledging the message
        removeMessageFromAutoVisibilityExtender(message);
    }

    private void removeMessageFromAutoVisibilityExtender(final Message message) {
        synchronized (waitingLock) {
            final MessageProcessingState valueStored = currentMessagesProcessing.remove(message);
            // Makes sure we only do this once for the message
            if (valueStored != null) {
                decoratorProperties.messageDoneProcessing(message);
                waitingLock.notify();
            }
        }
    }

    private void performBackgroundThread() {
        log.debug("Starting background thread for auto visibility extender");
        synchronized (waitingLock) {
            while (!currentMessagesProcessing.isEmpty()) {
                final Instant timeNow = Instant.now();
                final Duration maxDuration = decoratorProperties.maxDuration();
                final Duration bufferDuration = decoratorProperties.bufferDuration();

                interruptLongRunningThreads(timeNow, maxDuration);

                extendThreadsWithMoreTime(timeNow, bufferDuration);

                try {
                    waitUntilNextIteration(maxDuration);
                } catch (final InterruptedException interruptedException) {
                    break;
                }
            }
        }
        log.debug("Finished background thread for auto visibility extender");
    }

    private void interruptLongRunningThreads(final Instant timeNow, final Duration maxDuration) {
        final Map<Message, MessageProcessingState> messagesToInterrupt = currentMessagesProcessing
            .entrySet()
            .stream()
            .filter(messageStateEntry -> timeNow.compareTo(messageStateEntry.getValue().startTime().plus(maxDuration)) >= 0)
            .collect(CollectionUtils.pairsToMap());

        messagesToInterrupt.forEach(
            (message, state) -> {
                log.info("Interrupting message processing thread due to exceeded time for message {}", message.messageId());
                state.thread().interrupt();
                currentMessagesProcessing.remove(message);
            }
        );
    }

    /**
     * For each message that has hit the visibility timeout extension time, attempt to extend the visibility.
     *
     * <p>This method does not wait for the response from the visibility timeout extension and just assumes that it works.
     *
     * @param timeNow the time that this iteration started at
     * @param bufferDuration the amount of buffer time for the next visibility timeout extension
     */
    private void extendThreadsWithMoreTime(final Instant timeNow, final Duration bufferDuration) {
        final Map<Message, MessageProcessingState> messagesToExtend = currentMessagesProcessing
            .entrySet()
            .stream()
            .filter(messageStateEntry -> timeNow.compareTo(messageStateEntry.getValue().nextVisibilityExtensionTime()) >= 0)
            .collect(CollectionUtils.pairsToMap());

        List<Message> messageBatch = new ArrayList<>(AwsConstants.MAX_NUMBER_OF_MESSAGES_IN_BATCH);
        for (final Map.Entry<Message, MessageProcessingState> stateEntry : messagesToExtend.entrySet()) {
            final Message message = stateEntry.getKey();
            final MessageProcessingState state = stateEntry.getValue();
            log.info("Automatically extending visibility timeout of message {}", message.messageId());
            messageBatch.add(message);
            if (messageBatch.size() == AwsConstants.MAX_NUMBER_OF_MESSAGES_IN_BATCH) {
                extendMessageBatch(messageBatch);
                messageBatch.clear();
            }
            currentMessagesProcessing.put(
                message,
                ImmutableMessageProcessingState
                    .builder()
                    .from(state)
                    .nextVisibilityExtensionTime(timeNow.plus(decoratorProperties.visibilityTimeout(message).minus(bufferDuration)))
                    .build()
            );
        }

        if (!messageBatch.isEmpty()) {
            extendMessageBatch(messageBatch);
        }
    }

    private void extendMessageBatch(final List<Message> messageBatch) {
        sqsAsyncClient
            .changeMessageVisibilityBatch(
                builder ->
                    builder
                        .queueUrl(queueProperties.getQueueUrl())
                        .entries(
                            messageBatch
                                .stream()
                                .map(
                                    message ->
                                        ChangeMessageVisibilityBatchRequestEntry
                                            .builder()
                                            .id(message.messageId())
                                            .receiptHandle(message.receiptHandle())
                                            .visibilityTimeout((int) decoratorProperties.visibilityTimeout(message).getSeconds())
                                            .build()
                                )
                                .collect(Collectors.toList())
                        )
            )
            .whenComplete(
                (ignoredResponse, throwable) -> {
                    if (throwable != null) {
                        log.error(
                            "Error changing visibility timeout for message. The following messages were not extended: " +
                            messageBatch.stream().map(Message::messageId).collect(Collectors.toList()),
                            throwable
                        );
                    }

                    if (ignoredResponse.hasFailed()) {
                        log.error(
                            "Some messages failed to be have their visibility timeout changed: {}",
                            ignoredResponse.failed().stream().map(BatchResultErrorEntry::id).collect(Collectors.toList())
                        );
                    }
                }
            );
    }

    /**
     * If there are more messages that are currently processing, determine the next time that a message needs to be interrupted or extended and wait until
     * that.
     *
     * @param maxDuration the maximum amount of time to wait for a message
     * @throws InterruptedException if the thread was interrupted while waiting
     */
    private void waitUntilNextIteration(final Duration maxDuration) throws InterruptedException {
        final Optional<Instant> optionalEarliestNextUpdateTime = currentMessagesProcessing
            .values()
            .stream()
            .map(state -> determineEarliestTrigger(state, maxDuration))
            .min(Instant::compareTo);

        if (!optionalEarliestNextUpdateTime.isPresent()) {
            return;
        }

        final long nextTime = Instant.now().until(optionalEarliestNextUpdateTime.get(), ChronoUnit.MILLIS);
        if (nextTime <= 0) {
            return;
        }

        log.debug("Waiting {}ms to change visibility timeout", nextTime);
        waitingLock.wait(nextTime);
    }

    /**
     * Determines the next time that the message needs to be extended to stop its visibility from expiring.
     *
     * @param timeNow the time that this iteration started at
     * @param message the message to determine the visibility timeout for
     * @param bufferDuration the buffer to change the visibility timeout before it actually expires
     * @return the time to extend the message's visibility
     */
    private Instant nextExtensionTime(final Instant timeNow, final Message message, final Duration bufferDuration) {
        return timeNow.plus(decoratorProperties.visibilityTimeout(message)).minus(bufferDuration);
    }

    /**
     * Determines whether the earliest time for this message should be when it should be interrupted or the next visibility extension time.
     *
     * @param state the state of this message
     * @param maxDuration the maximum time the message should process
     * @return the next time that the message should be extended or interrupted
     */
    private static Instant determineEarliestTrigger(final MessageProcessingState state, final Duration maxDuration) {
        final Instant maxTime = state.startTime().plus(maxDuration);
        final Instant nextVisibilityExtensionTime = state.nextVisibilityExtensionTime();
        if (maxTime.isBefore(nextVisibilityExtensionTime)) {
            return maxTime;
        } else {
            return nextVisibilityExtensionTime;
        }
    }

    @Value.Immutable
    interface MessageProcessingState {
        /**
         * The thread that is processing this message.
         *
         * <p> This is used to interrupt the processing if has run too long.
         *
         * @return the thread processing the message
         */
        Thread thread();

        /**
         * The time that the message began processing.
         *
         * @return the start time for the message
         */
        Instant startTime();

        /**
         * The next time that the visibility of the message will need to be extended.
         *
         * <p> This includes the buffer time and therefore will occur before the message's timeout actually expires.
         *
         * @return the next visibility extension time
         */
        Instant nextVisibilityExtensionTime();
    }
}
