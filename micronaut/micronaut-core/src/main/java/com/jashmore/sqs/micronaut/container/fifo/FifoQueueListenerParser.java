package com.jashmore.sqs.micronaut.container.fifo;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.sqs.container.fifo.FifoMessageListenerContainerProperties;
import com.jashmore.sqs.micronaut.container.CoreAnnotationParser;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Parser that is used to transform a {@link FifoQueueListener} annotation to a {@link FifoMessageListenerContainerProperties}.
 */
public class FifoQueueListenerParser implements CoreAnnotationParser<FifoQueueListener, FifoMessageListenerContainerProperties> {

    private final Environment environment;

    public FifoQueueListenerParser(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public FifoMessageListenerContainerProperties parse(final FifoQueueListener annotation) {
        final Supplier<Integer> concurrencySupplier = concurrencySupplier(annotation);
        final Supplier<Duration> concurrencyPollingRateSupplier = concurrencyPollingRateSupplier(annotation);
        final Supplier<Integer> maximumMessagesInMessageGroupSupplier = maximumMessagesInGroupSupplier(annotation);
        final Supplier<Integer> maximumCachedMessageGroupsSupplier = maximumCachedMessageGroupsSupplier(annotation);
        final Supplier<Duration> errorBackoffTimeSupplier = errorBackoffTimeSupplier(annotation);
        final Supplier<Duration> getMessageVisibilityTimeoutSupplier = messageVisibilityTimeoutSupplier(annotation);
        final Supplier<Boolean> tryAndProcessAnyExtraRetrievedMessagesOnShutdownSupplier =
            tryAndProcessAnyExtraRetrievedMessagesOnShutdownSupplier(annotation);
        final Supplier<Boolean> interruptThreadsProcessingMessagesOnShutdownSupplier = interruptThreadsProcessingMessagesOnShutdownSupplier(
            annotation
        );
        return new FifoMessageListenerContainerProperties() {
            @Override
            public int concurrencyLevel() {
                return concurrencySupplier.get();
            }

            @Override
            public @Nullable Duration concurrencyPollingRate() {
                return concurrencyPollingRateSupplier.get();
            }

            @Override
            public @Nullable Duration errorBackoffTime() {
                return errorBackoffTimeSupplier.get();
            }

            @Override
            public int maximumMessagesInMessageGroup() {
                return maximumMessagesInMessageGroupSupplier.get();
            }

            @Override
            public int maximumCachedMessageGroups() {
                return maximumCachedMessageGroupsSupplier.get();
            }

            @Override
            public @Nullable Duration messageVisibilityTimeout() {
                return getMessageVisibilityTimeoutSupplier.get();
            }

            @Override
            public boolean tryAndProcessAnyExtraRetrievedMessagesOnShutdown() {
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
     * @see FifoMessageListenerContainerProperties#concurrencyLevel() for more details
     */
    protected Supplier<Integer> concurrencySupplier(final FifoQueueListener annotation) {
        final int concurrencyLevel;
        if (!StringUtils.hasText(annotation.concurrencyLevelString())) {
            concurrencyLevel = annotation.concurrencyLevel();
        } else {
            concurrencyLevel = Integer.parseInt(environment.getPlaceholderResolver()
                    .resolveRequiredPlaceholders(annotation.concurrencyLevelString()));
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
     * @see FifoMessageListenerContainerProperties#concurrencyPollingRate() for more details
     */
    protected Supplier<Duration> concurrencyPollingRateSupplier(final FifoQueueListener annotation) {
        return () -> null;
    }

    /**
     * Parse the annotation to construct a supplier that returns the maximum number of messages that should be downloaded per message group at once.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the maximum messages in group supplier
     * @see FifoMessageListenerContainerProperties#maximumMessagesInMessageGroup() for more details
     */
    protected Supplier<Integer> maximumMessagesInGroupSupplier(final FifoQueueListener annotation) {
        final int batchSize;
        if (!StringUtils.hasText(annotation.maximumMessagesInMessageGroupString())) {
            batchSize = annotation.maximumMessagesInMessageGroup();
        } else {
            batchSize = Integer.parseInt(environment.getPlaceholderResolver()
                    .resolveRequiredPlaceholders(annotation.maximumMessagesInMessageGroupString()));
        }

        return () -> batchSize;
    }

    /**
     * Parse the annotation to construct a supplier that returns the maximum number of message groups that can be downloaded at once.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the maximum message groups supplier
     * @see FifoMessageListenerContainerProperties#maximumCachedMessageGroups() for more details
     */
    protected Supplier<Integer> maximumCachedMessageGroupsSupplier(final FifoQueueListener annotation) {
        final int maximumCachedMessageGroups;
        if (!StringUtils.hasText(annotation.maximumCachedMessageGroupsString())) {
            maximumCachedMessageGroups = annotation.maximumCachedMessageGroups();
        } else {
            maximumCachedMessageGroups = Integer.parseInt(environment.getPlaceholderResolver()
                    .resolveRequiredPlaceholders(annotation.maximumCachedMessageGroupsString()));
        }
        return () -> maximumCachedMessageGroups;
    }

    /**
     * Parse the annotation to construct a supplier that returns the duration the container should back off if there was an error handling messages within
     * the framework.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the backoff time supplier
     * @see FifoMessageListenerContainerProperties#errorBackoffTime() for more details
     */
    protected Supplier<Duration> errorBackoffTimeSupplier(final FifoQueueListener annotation) {
        return () -> null;
    }

    /**
     * Parse the annotation to construct a supplier that returns the duration that the message should be invisible from other consumers.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the message visibility timeout supplier
     * @see FifoMessageListenerContainerProperties#messageVisibilityTimeout() for more details
     */
    protected Supplier<Duration> messageVisibilityTimeoutSupplier(final FifoQueueListener annotation) {
        final Duration messageVisibilityTimeout;
        if (!StringUtils.hasText(annotation.messageVisibilityTimeoutInSecondsString())) {
            if (annotation.messageVisibilityTimeoutInSeconds() <= 0) {
                messageVisibilityTimeout = null;
            } else {
                messageVisibilityTimeout = Duration.ofSeconds(annotation.messageVisibilityTimeoutInSeconds());
            }
        } else {
            messageVisibilityTimeout =
                Duration.ofSeconds(Integer.parseInt(environment.getPlaceholderResolver()
                        .resolveRequiredPlaceholders(annotation.messageVisibilityTimeoutInSecondsString())));
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
     * @see FifoMessageListenerContainerProperties#tryAndProcessAnyExtraRetrievedMessagesOnShutdown() for more details
     */
    protected Supplier<Boolean> tryAndProcessAnyExtraRetrievedMessagesOnShutdownSupplier(final FifoQueueListener annotation) {
        final boolean tryAndProcessAnyExtraRetrievedMessagesOnShutdown = annotation.tryAndProcessAnyExtraRetrievedMessagesOnShutdown();
        return () -> tryAndProcessAnyExtraRetrievedMessagesOnShutdown;
    }

    /**
     * Parse the annotation to construct a supplier that returns whether currently processing messages should be interrupted on shutdown.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the interrupt message processing on shutdown supplier
     * @see FifoMessageListenerContainerProperties#interruptThreadsProcessingMessagesOnShutdown() for more details
     */
    protected Supplier<Boolean> interruptThreadsProcessingMessagesOnShutdownSupplier(final FifoQueueListener annotation) {
        final boolean interruptThreadsProcessingMessagesOnShutdown = annotation.interruptThreadsProcessingMessagesOnShutdown();
        return () -> interruptThreadsProcessingMessagesOnShutdown;
    }
}
