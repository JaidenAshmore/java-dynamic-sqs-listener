package com.jashmore.sqs.util.retriever;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;

import com.jashmore.sqs.aws.AwsConstants;
import com.jashmore.sqs.util.properties.PropertyUtils;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

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
    public int safelyGetWaitTimeInSeconds(final Supplier<Integer> unsafeWaitTimeInSeconds) {
        final int waitTimeInSeconds = PropertyUtils.safelyGetPositiveIntegerValue(
                "messageWaitTimeInSeconds",
                unsafeWaitTimeInSeconds,
                MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS
        );

        if (waitTimeInSeconds > MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS) {
            log.warn("messageWaitTimeInSeconds provided({}) is greater than AWS maximum({}), using default instead", waitTimeInSeconds,
                    AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);
            return MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
        }

        return waitTimeInSeconds;
    }
}
