package com.jashmore.sqs.util;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

import java.util.function.Supplier;

/**
 * Provides helper methods for building {@link Supplier}s.
 *
 * <p>This has been implemented again to reduce dependencies on other modules, more specifically Guava.
 */
@UtilityClass
public final class Suppliers {
    /**
     * Memoize the value of the supplier for a given time period.
     *
     * @param timeoutInMs the amount of time in milliseconds to cache the value
     * @param delegate    the actual implementation of the supplier that will be called when the cache is stale
     * @param <T>         the type returned from the supplier
     * @return a supplier that will cache values on multiple calls
     */
    public static <T> Supplier<T> memoize(final long timeoutInMs, final Supplier<T> delegate) {
        Preconditions.checkArgument(timeoutInMs > 0);
        Preconditions.checkArgumentNotNull(delegate, "delegate");

        return new CachedSupplier<>(timeoutInMs, delegate);
    }

    /**
     * A cached supplier that will cache the value of the supplier for the given time period.
     *
     * @param <T> the type of the return value for the supplier
     */
    @RequiredArgsConstructor
    private static class CachedSupplier<T> implements Supplier<T> {
        /**
         * The total time to cache a value from the supplier in milliseconds.
         */
        private final long timeoutInMs;
        /**
         * The actual supplier that will be called if there is no valid cached value.
         */
        private final Supplier<T> delegateSupplier;

        /**
         * The time that the value was cached at.
         */
        private Long cachedTime;
        /**
         * The value that was cached.
         */
        private T cachedValue;

        @Override
        public synchronized T get() {
            final long currentTime = System.currentTimeMillis();
            final boolean isCacheStale = cachedTime == null || (currentTime - cachedTime) >= timeoutInMs;
            if (isCacheStale) {
                cachedValue = delegateSupplier.get();
                cachedTime = currentTime;
            }
            return cachedValue;
        }
    }
}
