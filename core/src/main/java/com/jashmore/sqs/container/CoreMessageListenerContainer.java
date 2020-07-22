package com.jashmore.sqs.container;

import static com.jashmore.sqs.container.CoreMessageListenerContainerConstants.DEFAULT_SHOULD_INTERRUPT_MESSAGE_PROCESSING_ON_SHUTDOWN;
import static com.jashmore.sqs.container.CoreMessageListenerContainerConstants.DEFAULT_SHOULD_PROCESS_EXTRA_MESSAGES_ON_SHUTDOWN;
import static com.jashmore.sqs.container.CoreMessageListenerContainerConstants.DEFAULT_SHUTDOWN_TIME;
import static com.jashmore.sqs.util.properties.PropertyUtils.safelyGetPositiveOrZeroDuration;
import static com.jashmore.sqs.util.thread.ThreadUtils.multiNamedThreadFactory;
import static com.jashmore.sqs.util.thread.ThreadUtils.singleNamedThreadFactory;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.jashmore.documentation.annotations.GuardedBy;
import com.jashmore.documentation.annotations.ThreadSafe;
import com.jashmore.documentation.annotations.VisibleForTesting;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.util.Preconditions;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.utils.StringUtils;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Container that allows for the safe start and stop of all the components for the SQS Library.
 *
 * <p>This container handles graceful shutdown by allowing any extra batched messages to be processed after the shutdown process has been triggered. It
 * also will wait for all background threads to finish, for example it will wait until all of the resolved messages in a {@link MessageResolver} to
 * be completed before finishing the shutdown.
 *
 * <p>This container expects a new instance of each component (e.g. {@link MessageResolver}) each time that it is started up to remove the need for each
 * component to maintain state between start up.
 */
@Slf4j
@ThreadSafe
public class CoreMessageListenerContainer implements MessageListenerContainer {
    private final String identifier;
    private final Supplier<MessageBroker> messageBrokerSupplier;
    private final Supplier<MessageRetriever> messageRetrieverSupplier;
    private final Supplier<MessageProcessor> messageProcessorSupplier;
    private final Supplier<MessageResolver> messageResolverSupplier;
    private final CoreMessageListenerContainerProperties properties;

    /**
     * The service that is running this container's thread.
     */
    @GuardedBy("this")
    private ExecutorService executorService;
    /**
     * Future that will be resolved when the container's thread has finished.
     */
    @GuardedBy("this")
    private CompletableFuture<?> containerFuture;

    public CoreMessageListenerContainer(final String identifier,
                                        final Supplier<MessageBroker> messageBrokerSupplier,
                                        final Supplier<MessageRetriever> messageRetrieverSupplier,
                                        final Supplier<MessageProcessor> messageProcessorSupplier,
                                        final Supplier<MessageResolver> messageResolverSupplier) {
        this(
                identifier,
                messageBrokerSupplier,
                messageRetrieverSupplier,
                messageProcessorSupplier,
                messageResolverSupplier,
                StaticCoreMessageListenerContainerProperties.builder().build()
        );
    }

    public CoreMessageListenerContainer(final String identifier,
                                        final Supplier<MessageBroker> messageBrokerSupplier,
                                        final Supplier<MessageRetriever> messageRetrieverSupplier,
                                        final Supplier<MessageProcessor> messageProcessorSupplier,
                                        final Supplier<MessageResolver> messageResolverSupplier,
                                        final CoreMessageListenerContainerProperties properties) {
        Preconditions.checkArgument(StringUtils.isNotBlank(identifier), "identifier should not be empty");

        this.identifier = identifier;
        this.messageBrokerSupplier = messageBrokerSupplier;
        this.messageRetrieverSupplier = messageRetrieverSupplier;
        this.messageProcessorSupplier = messageProcessorSupplier;
        this.messageResolverSupplier = messageResolverSupplier;
        this.properties = properties;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public synchronized CompletableFuture<?> start() {
        if (executorService != null) {
            log.info("Container '{}' has already been started. No action taken", identifier);
        } else {
            log.info("Container '{}' is being started", identifier);
            executorService = Executors.newSingleThreadExecutor(singleNamedThreadFactory(identifier + "-message-container"));
            containerFuture = CompletableFuture.runAsync(this::runContainer, executorService);
        }
        return containerFuture;
    }

    @Override
    public synchronized void stop() {
        stop(Duration.of(1, ChronoUnit.MINUTES));
    }

    @Override
    public synchronized void stop(final Duration duration) {
        final long shutdownTimeLimitInSeconds = duration.get(ChronoUnit.SECONDS);
        log.info("Container '{}' is being stopped and will wait {} seconds", identifier, shutdownTimeLimitInSeconds);
        try {
            if (executorService != null) {
                executorService.shutdownNow();
                final boolean isTerminated = executorService.awaitTermination(shutdownTimeLimitInSeconds, SECONDS);
                if (!isTerminated) {
                    log.error("Container '{}' did not shutdown in timeout", identifier);
                }
            }
        } catch (final InterruptedException interruptedException) {
            log.warn("Thread interrupted waiting for container to stop. All threads may not be successfully completed");
            Thread.currentThread().interrupt();
        } finally {
            executorService = null;
            containerFuture = null;
        }
    }

    /**
     * This contains the main execution for the container where it controls how the container is started and the process for gracefully shutting down.
     *
     * <p>This is visible for testing to simplify testing so that it doesn't need to be run on separate threads, etc.
     */
    @VisibleForTesting
    void runContainer() {
        try {
            final MessageRetriever messageRetriever = messageRetrieverSupplier.get();
            final MessageResolver messageResolver = messageResolverSupplier.get();
            final MessageBroker messageBroker = messageBrokerSupplier.get();
            final MessageProcessor messageProcessor = messageProcessorSupplier.get();

            final BlockingRunnable shutdownMessageResolver = startupMessageResolver(messageResolver);
            final ExecutorService messageProcessingExecutorService = buildMessageProcessingExecutorService();

            // As the AsyncMessageRetriever may have extra messages batched, they will be placed in here
            final Queue<Message> extraMessages = new LinkedList<>();

            final BlockingRunnable shutdownMessageRetriever = startupMessageRetriever(messageRetriever, extraMessages::addAll);
            log.info("Container '{}' is beginning to process messages", identifier);
            processMessagesFromRetriever(messageBroker, messageRetriever, messageProcessor, messageResolver, messageProcessingExecutorService);
            log.info("Container '{}' is being shutdown", identifier);
            log.debug("Container '{}' is shutting down MessageRetriever", identifier);
            shutdownMessageRetriever.run();
            log.debug("Container '{}' has stopped the MessageRetriever", identifier);
            if (!extraMessages.isEmpty() && shouldProcessAnyExtraRetrievedMessagesOnShutdown()) {
                log.info("Container '{}' is processing {} extra messages before shutdown", identifier, extraMessages.size());
                processExtraMessages(messageBroker, messageProcessor, messageResolver, messageProcessingExecutorService, extraMessages);
            }
            log.debug("Container '{}' is shutting down MessageProcessor threads", identifier);
            shutdownMessageProcessingThreads(messageProcessingExecutorService);
            log.debug("Container '{}' has shutdown the MessageProcessor threads", identifier);
            log.debug("Container '{}' is shutting down MessageResolver", identifier);
            shutdownMessageResolver.run();
            log.debug("Container '{}' has shutdown the MessageResolver", identifier);
            log.info("Container '{}' has stopped", identifier);
        } catch (final InterruptedException interruptedException) {
            log.error("Container '{}' was interrupted during the shutdown process.", identifier);
        } catch (RuntimeException runtimeException) {
            log.error("Unexpected error trying to start/stop the container", runtimeException);
        }
    }

    /**
     * Executes the main flow of the application for processing messages.
     *
     * <p>This performs the main execution by taking messages by calling into the {@link MessageBroker} which will get new messages from the
     * {@link MessageRetriever} which for each new message will be processed by the {@link MessageProcessor} and then finally resolved by calling into
     * the {@link MessageResolver}.
     *
     * <p>This will keep running until the thread is interrupted via a call to {@link #stop()}.
     *
     * @param messageBroker                    the broker that handles the concurrent processing of messages and how to flow messages between the components
     * @param messageRetriever                 the retriever for obtaining new messages
     * @param messageProcessor                 the processor that will execute the message
     * @param messageResolver                  the resolver that will resolve the message on successful processing
     * @param messageProcessingExecutorService the executor service that the message processing will run on
     */
    private void processMessagesFromRetriever(final MessageBroker messageBroker,
                                              final MessageRetriever messageRetriever,
                                              final MessageProcessor messageProcessor,
                                              final MessageResolver messageResolver,
                                              final ExecutorService messageProcessingExecutorService) throws InterruptedException {
        try {
            runBrokerUntilInterrupted(() -> messageBroker.processMessages(
                    messageProcessingExecutorService,
                    messageRetriever::retrieveMessage,
                    message -> messageProcessor.processMessage(message, () -> messageResolver.resolveMessage(message))
            ));
        } catch (final ExecutionException executionException) {
            log.error("Error processing messages", executionException.getCause());
        }
    }

    /**
     * This processes any extra messages that may have been batched by the {@link MessageRetriever}.
     *
     * @param messageBroker                    the broker that handles the concurrent processing of messages and how to flow messages between the components
     * @param messageProcessor                 the processor that will execute the message
     * @param messageResolver                  the resolver that will resolve the message on successful processing
     * @param messageProcessingExecutorService the executor service that the message processing will run on
     * @param messages                         the messages to be processed
     * @throws InterruptedException if the thread was interrupted during this process
     */
    private void processExtraMessages(final MessageBroker messageBroker,
                                      final MessageProcessor messageProcessor,
                                      final MessageResolver messageResolver,
                                      final ExecutorService messageProcessingExecutorService,
                                      final Queue<Message> messages) throws InterruptedException {
        try {
            runBrokerUntilInterrupted(() -> messageBroker.processMessages(
                    messageProcessingExecutorService,
                    () -> !messages.isEmpty(),
                    () -> CompletableFuture.completedFuture(messages.poll()),
                    message -> messageProcessor.processMessage(message, () -> messageResolver.resolveMessage(message))
            ));
        } catch (final ExecutionException executionException) {
            log.error("Exception thrown processing extra messages", executionException.getCause());
        }
    }

    /**
     * Run the provided {@link Runnable} on a message broker thread until it is interrupted in which case the {@link Runnable} should
     * also be interrupted.
     *
     * @param runnable the runnable to run which should keep running until an interruption
     * @throws InterruptedException if it was interrupted again waiting for the runnable to exit
     * @throws ExecutionException   if there was an error running the runnable
     */
    private void runBrokerUntilInterrupted(final BlockingRunnable runnable) throws InterruptedException, ExecutionException {
        final ExecutorService messageBrokerExecutorService = Executors.newSingleThreadExecutor(singleNamedThreadFactory(identifier + "-message-broker"));
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    runnable.run();
                } catch (final InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }, messageBrokerExecutorService).get();
        } catch (final InterruptedException interruptedException) {
            // We are handling this explicitly
        } finally {
            log.debug("Container '{}' is shutting down MessageBroker", identifier);
            shutdownMessageBroker(messageBrokerExecutorService);
            log.debug("Container '{}' has shutdown the MessageBroker", identifier);
        }
    }

    /**
     * Shuts down the {@link ExecutorService} that is processing the messages.
     *
     * <p>Depending on {@link CoreMessageListenerContainerProperties#shouldInterruptThreadsProcessingMessagesOnShutdown()} it will interrupt the message
     * processing threads.
     *
     * @param executorService the executor service used to run these messages
     * @throws InterruptedException if the thread was interrupted during this process
     */
    private void shutdownMessageProcessingThreads(final ExecutorService executorService) throws InterruptedException {
        if (shouldInterruptMessageProcessingThreadsOnShutdown()) {
            log.debug("Container '{}' is interrupting and then waiting for all message processing threads to finish", identifier);
            executorService.shutdownNow();
        } else {
            log.debug("Container '{}' is waiting for all message processing threads to finish", identifier);
            executorService.shutdown();
        }

        final Duration shutdownTimeout = safelyGetPositiveOrZeroDuration("messageProcessingShutdownTimeout",
                properties::getMessageProcessingShutdownTimeout, DEFAULT_SHUTDOWN_TIME);
        final boolean messageProcessingTerminated = executorService.awaitTermination(shutdownTimeout.getSeconds(), SECONDS);
        if (!messageProcessingTerminated) {
            log.error("Container '{}' did not shutdown MessageProcessor threads within {} seconds", identifier, shutdownTimeout.getSeconds());
        }
    }

    /**
     * Stop the message broker thread and wait for the configured amount of time to complete.
     *
     * @param executorService the executor service for the message broker
     * @throws InterruptedException if the thread was interrupted while waiting for the service to stop
     */
    private void shutdownMessageBroker(final ExecutorService executorService) throws InterruptedException {
        executorService.shutdownNow();

        final Duration shutdownTimeout = safelyGetPositiveOrZeroDuration("messageBrokerShutdownTimeout",
                properties::getMessageBrokerShutdownTimeout, DEFAULT_SHUTDOWN_TIME);
        final boolean terminationResult = executorService.awaitTermination(shutdownTimeout.getSeconds(), SECONDS);
        if (!terminationResult) {
            log.error("Container '{}' did not shutdown MessageBroker within {} seconds", getIdentifier(), shutdownTimeout.getSeconds());
        }
    }

    /**
     * Start the background thread of the {@link MessageRetriever}, returning a {@link BlockingRunnable} that can be executed
     * when it needs to be shutdown.
     *
     * <p>A callback is provided that will be called when the {@link MessageRetriever} finishes which will contain a drain of any of the extra messages
     * that had not been processed yet.
     *
     * @param messageRetriever      the retriever to start
     * @param extraMessagesConsumer the callback for consuming leftover messages
     * @return the method for shutting down the retriever background thread
     */
    private BlockingRunnable startupMessageRetriever(final MessageRetriever messageRetriever,
                                                     final Consumer<List<Message>> extraMessagesConsumer) {
        final ExecutorService executorService = Executors.newSingleThreadExecutor(singleNamedThreadFactory(getIdentifier() + "-message-retriever"));
        CompletableFuture.supplyAsync(messageRetriever::run, executorService)
                .thenAccept(extraMessagesConsumer);
        return () -> {
            executorService.shutdownNow();

            final Duration shutdownTimeout = safelyGetPositiveOrZeroDuration("messageRetrieverShutdownTimeout",
                    properties::getMessageRetrieverShutdownTimeout, DEFAULT_SHUTDOWN_TIME);
            final boolean retrieverShutdown = executorService.awaitTermination(shutdownTimeout.getSeconds(), SECONDS);
            if (!retrieverShutdown) {
                log.error("Container '{}' did not shutdown MessageRetriever within {} seconds", getIdentifier(), shutdownTimeout.getSeconds());
            }
        };
    }

    /**
     * Start a background thread of the {@link MessageResolver}, returning a {@link BlockingRunnable} that can be executed
     * when it needs to be shutdown.
     *
     * @param messageResolver the resolver to start if it is async
     * @return the optional {@link ExecutorService} for this background thread if it was started
     */
    private BlockingRunnable startupMessageResolver(final MessageResolver messageResolver) {
        final ExecutorService executorService = Executors.newSingleThreadExecutor(singleNamedThreadFactory(getIdentifier() + "-message-resolver"));
        CompletableFuture.runAsync(messageResolver::run, executorService);
        return () -> {
            executorService.shutdownNow();

            final Duration shutdownTimeout = safelyGetPositiveOrZeroDuration("messageResolverShutdownTimeout",
                    properties::getMessageResolverShutdownTimeout, DEFAULT_SHUTDOWN_TIME);
            final boolean messageResolverShutdown = executorService.awaitTermination(shutdownTimeout.getSeconds(), SECONDS);
            if (!messageResolverShutdown) {
                log.error("Container '{}' did not shutdown MessageResolver within {} seconds", getIdentifier(), shutdownTimeout.getSeconds());
            }
        };
    }

    /**
     * Build the {@link ExecutorService} that will be used for the threads that are processing the messages.
     *
     * @return the executor service that will be used for processing messages
     */
    private ExecutorService buildMessageProcessingExecutorService() {
        return Executors.newCachedThreadPool(multiNamedThreadFactory(getIdentifier() + "-message-processing"));
    }

    private boolean shouldInterruptMessageProcessingThreadsOnShutdown() {
        return Optional.ofNullable(properties.shouldInterruptThreadsProcessingMessagesOnShutdown())
                .orElse(DEFAULT_SHOULD_INTERRUPT_MESSAGE_PROCESSING_ON_SHUTDOWN);
    }

    private boolean shouldProcessAnyExtraRetrievedMessagesOnShutdown() {
        return Optional.ofNullable(properties.shouldProcessAnyExtraRetrievedMessagesOnShutdown())
                .orElse(DEFAULT_SHOULD_PROCESS_EXTRA_MESSAGES_ON_SHUTDOWN);
    }

    /**
     * Similar to a {@link Runnable} but it allows for {@link InterruptedException}s to be thrown.
     */
    @FunctionalInterface
    private interface BlockingRunnable {
        /**
         * Run the method.
         *
         * @throws InterruptedException if the thread was interrupted during execution
         */
        void run() throws InterruptedException;
    }
}
