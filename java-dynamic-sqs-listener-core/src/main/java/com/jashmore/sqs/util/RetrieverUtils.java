package com.jashmore.sqs.util;

import com.jashmore.sqs.aws.AwsConstants;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Utility functions that are helpful for the {@link com.jashmore.sqs.retriever.MessageRetriever}.
 */
@Slf4j
@UtilityClass
public class RetrieverUtils {
    /**
     * Gets the wait time in seconds, defaulting to {@link AwsConstants#MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS} if it is not present or invalid.
     *
     * @param unsafeWaitTimeInSeconds the original wait time in seconds
     * @return the amount of time to wait for messages from SQS
     */
    public int safelyGetWaitTimeInSeconds(final Integer unsafeWaitTimeInSeconds) {
        return Optional.ofNullable(unsafeWaitTimeInSeconds)
                .filter(waitTimeInSeconds -> {
                    if (waitTimeInSeconds <= 0) {
                        log.warn("Non-positive messageWaitTimeInSeconds provided({}), using default instead", waitTimeInSeconds,
                                AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
                        return false;
                    }

                    if (waitTimeInSeconds > AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS) {
                        log.warn("messageWaitTimeInSeconds provided({}) is greater than AWS maximum({}), using default instead", waitTimeInSeconds,
                                AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
                        return false;
                    }

                    return true;
                })
                .orElse(AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
    }

    /**
     * Get the amount of time in milliseconds that the thread should wait after a failure to get a message.
     *
     * <p>It is safe by looking for cases where the backoff time is null or in an invalid value, like being negative, and converting it to the default value
     * if it is not valid.
     *
     * @param unsafeBackoffTimeInMilliseconds  the original backoff time
     * @param defaultBackoffTimeInMilliseconds the default backoff time use if the original time is not valid
     * @return the amount of time to backoff on errors in milliseconds
     */
    public long safelyGetBackoffTime(final Long unsafeBackoffTimeInMilliseconds, final long defaultBackoffTimeInMilliseconds) {
        return Optional.ofNullable(unsafeBackoffTimeInMilliseconds)
                .filter(backoffTimeInMilliseconds -> {
                    if (backoffTimeInMilliseconds > 0) {
                        return true;
                    } else {
                        log.warn("Non-positive errorBackoffTimeInMilliseconds provided({}), using default instead", backoffTimeInMilliseconds);
                        return false;
                    }
                })
                .orElse(defaultBackoffTimeInMilliseconds);
    }
}
