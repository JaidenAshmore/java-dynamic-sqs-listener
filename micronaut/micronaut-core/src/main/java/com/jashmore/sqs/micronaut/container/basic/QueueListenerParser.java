package com.jashmore.sqs.micronaut.container.basic;

import com.jashmore.documentation.annotations.Max;
import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.Positive;
import com.jashmore.documentation.annotations.PositiveOrZero;
import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.container.batching.BatchingMessageListenerContainerProperties;
import com.jashmore.sqs.micronaut.container.CoreAnnotationParser;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Parser that is used to transform a {@link QueueListener} annotation to a {@link BatchingMessageListenerContainerProperties}.
 */
public class QueueListenerParser implements CoreAnnotationParser<QueueListener, BatchingMessageListenerContainerProperties> {

    private final Environment environment;

    public QueueListenerParser(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public BatchingMessageListenerContainerProperties parse(QueueListener annotation) {
        final Supplier<Integer> concurrencySupplier = concurrencySupplier(annotation);
        final Supplier<Duration> concurrencyPollingRateSupplier = concurrencyPollingRateSupplier(annotation);
        final Supplier<Integer> batchSizeSupplier = batchSizeSupplier(annotation);
        final Supplier<Duration> batchingPeriodSupplier = batchingPeriodSupplier(annotation);
        final Supplier<Duration> errorBackoffTimeSupplier = errorBackoffTimeSupplier(annotation);
        final Supplier<Duration> messageVisibilityTimeoutSupplier = messageVisibilityTimeoutSupplier(annotation);
        final Supplier<Boolean> tryAndProcessAnyExtraRetrievedMessagesOnShutdownSupplier =
            tryAndProcessAnyExtraRetrievedMessagesOnShutdownSupplier(annotation);
        final Supplier<Boolean> interruptThreadsProcessingMessagesOnShutdownSupplier = interruptThreadsProcessingMessagesOnShutdownSupplier(
            annotation
        );
        return new BatchingMessageListenerContainerProperties() {
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
            @Max(AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS)
            @Override
            public int batchSize() {
                return batchSizeSupplier.get();
            }

            @Nullable
            @Positive
            @Override
            public Duration getBatchingPeriod() {
                return batchingPeriodSupplier.get();
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
     * @see BatchingMessageListenerContainerProperties#concurrencyLevel() for more details
     */
    protected Supplier<Integer> concurrencySupplier(final QueueListener annotation) {
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
     * @see BatchingMessageListenerContainerProperties#concurrencyPollingRate() for more details
     */
    protected Supplier<Duration> concurrencyPollingRateSupplier(final QueueListener annotation) {
        return () -> null;
    }

    /**
     * Parse the annotation to construct a supplier that returns the maximum number of messages that should be downloaded per message group at once.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the maximum messages in group supplier
     * @see BatchingMessageListenerContainerProperties#batchSize() for more details
     */
    protected Supplier<Integer> batchSizeSupplier(final QueueListener annotation) {
        final int batchSize;
        if (!StringUtils.hasText(annotation.batchSizeString())) {
            batchSize = annotation.batchSize();
        } else {
            batchSize = Integer.parseInt(environment.getPlaceholderResolver()
                    .resolveRequiredPlaceholders(annotation.batchSizeString()));
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
     * @see BatchingMessageListenerContainerProperties#getBatchingPeriod() for more details
     */
    protected Supplier<Duration> batchingPeriodSupplier(final QueueListener annotation) {
        final Duration batchingPeriod;
        if (!StringUtils.hasText(annotation.batchingPeriodInMsString())) {
            batchingPeriod = Duration.ofMillis(annotation.batchingPeriodInMs());
        } else {
            batchingPeriod = Duration.ofMillis(Integer.parseInt(environment.getPlaceholderResolver()
                    .resolveRequiredPlaceholders(annotation.batchingPeriodInMsString())));
        }
        return () -> batchingPeriod;
    }

    /**
     * Parse the annotation to construct a supplier that returns the duration the container should back off if there was an error handling messages within
     * the framework.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the backoff time supplier
     * @see BatchingMessageListenerContainerProperties#errorBackoffTime() for more details
     */
    protected Supplier<Duration> errorBackoffTimeSupplier(final QueueListener annotation) {
        return () -> null;
    }

    /**
     * Parse the annotation to construct a supplier that returns the duration that the message should be invisible from other consumers.
     *
     * <p>Can be overridden to provide custom logic.
     *
     * @param annotation the annotation to parse
     * @return the message visibility timeout supplier
     * @see BatchingMessageListenerContainerProperties#messageVisibilityTimeout() for more details
     */
    protected Supplier<Duration> messageVisibilityTimeoutSupplier(final QueueListener annotation) {
        final Duration messageVisibilityTimeout;
        if (!StringUtils.hasText(annotation.messageVisibilityTimeoutInSecondsString())) {
            final int visibilityTimeout = annotation.messageVisibilityTimeoutInSeconds();
            if (visibilityTimeout < 0) {
                messageVisibilityTimeout = null;
            } else {
                messageVisibilityTimeout = Duration.ofSeconds(visibilityTimeout);
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
     * @see BatchingMessageListenerContainerProperties#processAnyExtraRetrievedMessagesOnShutdown() () for more details
     */
    protected Supplier<Boolean> tryAndProcessAnyExtraRetrievedMessagesOnShutdownSupplier(final QueueListener annotation) {
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
     * @see BatchingMessageListenerContainerProperties#interruptThreadsProcessingMessagesOnShutdown() for more details
     */
    protected Supplier<Boolean> interruptThreadsProcessingMessagesOnShutdownSupplier(final QueueListener annotation) {
        final boolean interruptThreadsProcessingMessagesOnShutdown = annotation.interruptThreadsProcessingMessagesOnShutdown();
        return () -> interruptThreadsProcessingMessagesOnShutdown;
    }
}
