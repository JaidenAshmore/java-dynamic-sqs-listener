package com.jashmore.sqs.util.concurrent;

import lombok.experimental.UtilityClass;

import java.util.concurrent.CompletableFuture;

@UtilityClass
public class CompletableFutureUtils {
    /**
     * Creates a new {@link CompletableFuture} that is completed exceptionally with the provided {@link Throwable}.
     *
     * @param throwable the throwable to complete the {@link CompletableFuture} with
     * @param <T> the type that the future is returning
     * @return a future that is completed exceptionally
     */
    public static <T> CompletableFuture<T> completedExceptionally(final Throwable throwable) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }
}
