package com.jashmore.sqs.util.concurrent;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CompletableFutureUtils {

    /**
     * Creates a new {@link CompletableFuture} that is completed exceptionally with the provided {@link Throwable}.
     *
     * @param throwable the throwable to complete the {@link CompletableFuture} with
     * @param <T> the type that the future is returning
     * @return a future that is completed exceptionally
     */
    public <T> CompletableFuture<T> completedExceptionally(final Throwable throwable) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    /**
     * Wait for all of the futures to complete and return a single future containing the list of results.
     *
     * @param futures the futures to wait for
     * @param <T> the type that the future returns
     * @return a future that will complete if all futures are completed successfully
     */
    public <T> CompletableFuture<List<T>> allOf(final List<CompletableFuture<T>> futures) {
        return CompletableFuture
            .allOf(futures.toArray(new CompletableFuture<?>[0]))
            .thenApply(ignored -> futures.stream().map(CompletableFuture::join).collect(toList()));
    }
}
