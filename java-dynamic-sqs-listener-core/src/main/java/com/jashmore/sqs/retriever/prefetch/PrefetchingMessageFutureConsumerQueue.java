package com.jashmore.sqs.retriever.prefetch;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Queue that allows for the persistence of extra {@link CompletableFuture}s and {@link Message}s and when there are two matching together, resolve the
 * {@link CompletableFuture} with this {@link Message}.
 *
 * <p>There did not seem to be any Java Concurrent data structure that provide this sort of functionality so this custom data structure was written. Note that
 * it was made to be very specific to this use case as I do not desire it to be re-usable.  It also allows for simpler code due to certain assumptions like
 * allowing unlimited {@link CompletableFuture}s to be submitted and a fixed number of {@link Message}s.
 *
 * <p>This data structure's invariant states that there may be batched {@link CompletableFuture}s <b>OR</b> batched {@link Message} but there should never be a
 * state where both types are batched as that implies that they should have had that message resolve that corresponding future. The order of each object
 * should also be maintained in that the first future should be resolved with the first message received.
 *
 * <p>For performance reasons, the {@link CompletableFuture} is resolved outside of obtaining the {@link #lock}.
 *
 * <p>This implementation must be thread safe as there can be multiple threads submitting {@link CompletableFuture}s concurrently, though it is assumed that
 * there is only a single thread submitting {@link Message}s.
 */
@Slf4j
@ThreadSafe
class PrefetchingMessageFutureConsumerQueue {
    private final Queue<CompletableFuture<Message>> futureQueue;
    private final Queue<Message> messageQueue;
    private final Integer messageCapacity;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition messageQueueNotFull = lock.newCondition();

    /**
     * Constructor.
     *
     * @param messageCapacity the maximum number of messages to batch before {@link #pushMessage(Message)} blocks until
     *     {@link #pushCompletableFuture(CompletableFuture)} is called
     */
    PrefetchingMessageFutureConsumerQueue(final Integer messageCapacity) {
        this.futureQueue = new LinkedList<>();
        this.messageQueue = new LinkedList<>();
        this.messageCapacity = messageCapacity;
    }

    /**
     * Add a new {@link CompletableFuture} and if there is a {@link Message} in the queue resolve the future with this message. If there is no {@link Message}
     * currently in the queue, add the {@link CompletableFuture} into the queue which will be resolved when more {@link Message}s are added.
     *
     * @param completableFuture the future to include in the queue
     */
    void pushCompletableFuture(@Nonnull CompletableFuture<Message> completableFuture) {
        final Message message;
        lock.lock();
        try {
            message = messageQueue.poll();
            // We took a message of the queue resulting in it not being full anymore so we should signal this
            if (message != null && (messageQueue.size() + 1 == messageCapacity)) {
                messageQueueNotFull.signal();
            }

            if (message == null) {
                futureQueue.add(completableFuture);
            }
        } finally {
            lock.unlock();
        }

        if (message != null) {
            completableFuture.complete(message);
        }
    }

    /**
     * Add a new {@link Message} and if there is already a {@link CompletableFuture} in the queue resolve the future with this message. If there is
     * no {@link CompletableFuture} internally it will add it onto the {@link Message} queue.
     *
     * <p>Adding it to the {@link Message} queue is a blocking operation as there is a capacity for this queue so the thread will block until a
     * {@link CompletableFuture} is pushed by the {@link #pushCompletableFuture(CompletableFuture)} method.
     *
     * @param message the message to add
     * @throws InterruptedException if the thread was interrupted while waiting for the lock or adding a message onto the internal message queue
     */
    void pushMessage(@Nonnull final Message message) throws InterruptedException {
        CompletableFuture<Message> completableFuture;
        lock.lockInterruptibly();
        try {
            // Keep waiting for an empty slot in the message queue. Note that each iteration rechecks if a future was added in the case that multiple futures
            // were added since an empty slot opened
            while ((completableFuture = futureQueue.poll()) == null && messageQueue.size() == messageCapacity) {
                messageQueueNotFull.await();
            }

            if (completableFuture == null) {
                messageQueue.add(message);
            }
        } finally {
            lock.unlock();
        }

        if (completableFuture != null) {
            completableFuture.complete(message);
        }
    }

    /**
     * This will block the current thread until there is an available slot in the message queue.
     *
     * @throws InterruptedException if the thread was interrupted while waiting for a slot
     */
    void blockUntilFreeSlotForMessage() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (messageQueue.size() == messageCapacity) {
                messageQueueNotFull.await();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get a snapshot of the total number of {@link Message}s that are currently batched.
     *
     * @return the total messages batched
     */
    int getNumberOfBatchedMessages() {
        return messageQueue.size();
    }

    /**
     * Drain the queues (thus emptying) and return the messages to be resolved as well as the messages that have not been used yet.
     *
     * @return the queues of futures and messages that were in this queue
     */
    QueueDrain drain() {
        lock.lock();
        try {
            final LinkedList<CompletableFuture<Message>> futuresWaitingForMessages = new LinkedList<>(futureQueue);
            final LinkedList<Message> messagesAvailableForProcessing = new LinkedList<>(messageQueue);
            futureQueue.clear();
            messageQueue.clear();
            return QueueDrain.builder()
                    .futuresWaitingForMessages(futuresWaitingForMessages)
                    .messagesAvailableForProcessing(messagesAvailableForProcessing)
                    .build();
        } finally {
            lock.unlock();
        }
    }
}
