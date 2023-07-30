package com.jashmore.sqs.broker.util;

import com.jashmore.sqs.broker.MessageBroker;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.model.Message;

@Slf4j
@UtilityClass
public class MessageBrokerTestUtils {

    public static Future<?> runBrokerProcessMessageOnThread(
        final MessageBroker broker,
        final Supplier<CompletableFuture<Message>> messageRetriever,
        final Function<Message, CompletableFuture<?>> messageConsumer,
        final ExecutorService brokerExecutorService
    ) {
        return brokerExecutorService.submit(() -> {
            final ExecutorService messageProcessingExecutorService = Executors.newCachedThreadPool();
            try {
                broker.processMessages(messageProcessingExecutorService, messageRetriever, messageConsumer);
            } catch (InterruptedException interruptedException) {
                // do nothing
            } finally {
                log.info("Shutting down processing threads service");
                messageProcessingExecutorService.shutdown();
            }
        });
    }

    public static Future<?> runBrokerProcessMessageOnThread(
        final MessageBroker broker,
        final BooleanSupplier keepProcessingMessages,
        final Supplier<CompletableFuture<Message>> messageRetriever,
        final Function<Message, CompletableFuture<?>> messageConsumer,
        final ExecutorService brokerExecutorService
    ) {
        return brokerExecutorService.submit(() -> {
            final ExecutorService executorService = Executors.newCachedThreadPool();
            try {
                broker.processMessages(executorService, keepProcessingMessages, messageRetriever, messageConsumer);
            } catch (InterruptedException interruptedException) {
                // do nothing
            } finally {
                executorService.shutdownNow();
            }
        });
    }

    public static Function<Message, CompletableFuture<?>> processingMessageWillBlockUntilInterrupted(
        final ExecutorService messageProcessorExecutorService
    ) {
        return processingMessageWillBlockUntilInterrupted(null, messageProcessorExecutorService);
    }

    public static Function<Message, CompletableFuture<?>> processingMessageWillBlockUntilInterrupted(
        final CountDownLatch messageProcessingLatch,
        final ExecutorService messageProcessorExecutorService
    ) {
        return processingMessageWillBlockUntilInterrupted(messageProcessingLatch, () -> {}, messageProcessorExecutorService);
    }

    public static Function<Message, CompletableFuture<?>> processingMessageWillBlockUntilInterrupted(
        final CountDownLatch messageProcessingLatch,
        final Runnable runnableCalledOnMessageProcessing,
        final ExecutorService messageProcessorExecutorService
    ) {
        return message ->
            CompletableFuture.runAsync(
                () -> {
                    runnableCalledOnMessageProcessing.run();
                    if (messageProcessingLatch != null) {
                        messageProcessingLatch.countDown();
                    }
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (final InterruptedException interruptedException) {
                        //expected
                    }
                },
                messageProcessorExecutorService
            );
    }
}
