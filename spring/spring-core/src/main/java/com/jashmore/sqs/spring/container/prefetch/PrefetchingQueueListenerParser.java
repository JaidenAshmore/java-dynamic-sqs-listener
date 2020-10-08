package com.jashmore.sqs.spring.container.prefetch;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.container.prefetching.PrefetchingMessageListenerContainerProperties;
import com.jashmore.sqs.spring.container.CoreAnnotationParser;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Parser that is used to transform a {@link PrefetchingQueueListener} annotation to a {@link PrefetchingMessageListenerContainerProperties}.
 */
public class PrefetchingQueueListenerParser
    implements CoreAnnotationParser<PrefetchingQueueListener, PrefetchingMessageListenerContainerProperties> {
    private final Environment environment;

    public PrefetchingQueueListenerParser(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public PrefetchingMessageListenerContainerProperties parse(final PrefetchingQueueListener annotation) {
        final Supplier<Integer> concurrencySupplier = concurrencySupplier(annotation);
        final Supplier<Duration> concurrencyPollingRateSupplier = concurrencyPollingRateSupplier(annotation);
        final Supplier<Integer> desiredPrefetchedMessagesSupplier = desiredMinPrefetchedMessagesSupplier(annotation);
        final Supplier<Integer> maxPrefetchedMessagesSupplier = maxPrefetchedMessagesSupplier(annotation);
        final Supplier<Duration> errorBackoffTimeSupplier = errorBackoffTimeSupplier(annotation);
        final Supplier<Duration> messageVisibilityTimeoutSupplier = messageVisibilityTimeoutSupplier(annotation);
        final Supplier<Boolean> tryAndProcessAnyExtraRetrievedMessagesOnShutdownSupplier = tryAndProcessAnyExtraRetrievedMessagesOnShutdownSupplier(
            annotation
        );
        final Supplier<Boolean> interruptThreadsProcessingMessagesOnShutdownSupplier = interruptThreadsProcessingMessagesOnShutdownSupplier(
            annotation
        );

        return new PrefetchingMessageListenerContainerProperties() {

            @PositiveOrZero
            @Override
            public int concurrencyLevel() {
                return concurrencySupplier.get();
            }

            @Nullable
            @Override
            public Duration concurrencyPollingRate() {
                return concurrencyPollingRateSupplier.get();
            }

            @Nullable
            @Override
            public Duration errorBackoffTime() {
                return errorBackoffTimeSupplier.get();
            }

            @Positive
            @Override
            public int desiredMinPrefetchedMessages() {
                return desiredPrefetchedMessagesSupplier.get();
            }

            @Positive
            @Override
            public int maxPrefetchedMessages() {
                return maxPrefetchedMessagesSupplier.get();
            }

            @Nullable
            @Positive
            @Override
            public Duration messageVisibilityTimeout() {
                return messageVisibilityTimeoutSupplier.get();
            }

            @Override
            public boolean processAnyExtraRetrievedMessagesOnShutdown() {
                return tryAndProcessAnyExtraRetrievedMessagesOnShutdownSupplier.get();
            }

            @Override
            public boolean interruptThreadsProcessingMessagesOnShutdown() {
                return interruptThreadsProcessingMessagesOnShutdownSupplier.get();
            }
        };
    }

    /**
     * Parse the annotation to construct a supplier that returns the concurrency rate for the listener.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the concurrency supplier
     * @see PrefetchingMessageListenerContainerProperties#concurrencyLevel() for more details
     */
    protected Supplier<Integer> concurrencySupplier(final PrefetchingQueueListener annotation) {
        final int concurrencyLevel;
        if (StringUtils.isEmpty(annotation.concurrencyLevelString())) {
            concurrencyLevel = annotation.concurrencyLevel();
        } else {
            concurrencyLevel = Integer.parseInt(environment.resolvePlaceholders(annotation.concurrencyLevelString()));
        }
        return () -> concurrencyLevel;
    }

    /**
     * Parse the annotation to construct a supplier that returns the concurrency polling rate for the listener.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the concurrency supplier
     * @see PrefetchingMessageListenerContainerProperties#concurrencyPollingRate() for more details
     */
    protected Supplier<Duration> concurrencyPollingRateSupplier(final PrefetchingQueueListener annotation) {
        return () -> null;
    }

    /**
     * Parse the annotation to construct a supplier that returns the minimum desired messages to be prefetched.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the message visibility timeout supplier
     * @see PrefetchingMessageListenerContainerProperties#desiredMinPrefetchedMessages() for more details
     */
    protected Supplier<Integer> desiredMinPrefetchedMessagesSupplier(final PrefetchingQueueListener annotation) {
        final int desiredMinPrefetchedMessages;
        if (StringUtils.isEmpty(annotation.desiredMinPrefetchedMessagesString())) {
            desiredMinPrefetchedMessages = annotation.desiredMinPrefetchedMessages();
        } else {
            desiredMinPrefetchedMessages =
                Integer.parseInt(environment.resolvePlaceholders(annotation.desiredMinPrefetchedMessagesString()));
        }
        return () -> desiredMinPrefetchedMessages;
    }

    /**
     * Parse the annotation to construct a supplier that returns the maximum number of messages that can be prefetched.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the max prefetched messages supplier
     * @see PrefetchingMessageListenerContainerProperties#maxPrefetchedMessages() for more details
     */
    protected Supplier<Integer> maxPrefetchedMessagesSupplier(final PrefetchingQueueListener annotation) {
        final int maxPrefetchedMessages;
        if (StringUtils.isEmpty(annotation.maxPrefetchedMessagesString())) {
            maxPrefetchedMessages = annotation.maxPrefetchedMessages();
        } else {
            maxPrefetchedMessages = Integer.parseInt(environment.resolvePlaceholders(annotation.maxPrefetchedMessagesString()));
        }
        return () -> maxPrefetchedMessages;
    }

    /**
     * Parse the annotation to construct a supplier that returns the duration the container should back off if there was an error handling messages within
     * the framework.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the backoff time supplier
     * @see PrefetchingMessageListenerContainerProperties#errorBackoffTime() for more details
     */
    protected Supplier<Duration> errorBackoffTimeSupplier(final PrefetchingQueueListener annotation) {
        return () -> null;
    }

    /**
     * Parse the annotation to construct a supplier that returns the duration that the message should be invisible from other consumers.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the message visibility timeout supplier
     * @see PrefetchingMessageListenerContainerProperties#messageVisibilityTimeout() for more details
     */
    protected Supplier<Duration> messageVisibilityTimeoutSupplier(final PrefetchingQueueListener annotation) {
        final Duration messageVisibilityTimeout;
        if (StringUtils.isEmpty(annotation.messageVisibilityTimeoutInSecondsString())) {
            messageVisibilityTimeout = Duration.ofSeconds(annotation.messageVisibilityTimeoutInSeconds());
        } else {
            messageVisibilityTimeout =
                Duration.ofSeconds(Integer.parseInt(environment.resolvePlaceholders(annotation.messageVisibilityTimeoutInSecondsString())));
        }

        return () -> messageVisibilityTimeout;
    }

    /**
     * Parse the annotation to construct a supplier that returns whether any extra messages downloaded should be processed before shutdown.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the process extra messages supplier
     * @see PrefetchingMessageListenerContainerProperties#processAnyExtraRetrievedMessagesOnShutdown() () for more details
     */
    protected Supplier<Boolean> tryAndProcessAnyExtraRetrievedMessagesOnShutdownSupplier(final PrefetchingQueueListener annotation) {
        final boolean tryAndProcessAnyExtraRetrievedMessagesOnShutdown = annotation.processAnyExtraRetrievedMessagesOnShutdown();
        return () -> tryAndProcessAnyExtraRetrievedMessagesOnShutdown;
    }

    /**
     * Parse the annotation to construct a supplier that returns whether currently processing messages should be interrupted on shutdown.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the interrupt message processing on shutdown supplier
     * @see PrefetchingMessageListenerContainerProperties#interruptThreadsProcessingMessagesOnShutdown() for more details
     */
    protected Supplier<Boolean> interruptThreadsProcessingMessagesOnShutdownSupplier(final PrefetchingQueueListener annotation) {
        final boolean interruptThreadsProcessingMessagesOnShutdown = annotation.interruptThreadsProcessingMessagesOnShutdown();
        return () -> interruptThreadsProcessingMessagesOnShutdown;
    }
}
