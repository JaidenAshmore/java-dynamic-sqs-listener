package com.jashmore.sqs.aws;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class AwsConstants {
    /**
     * AWS has a maximum wait time of for receiving a message and some implementations like ElasticMQ will
     * throw errors for any value greater than this.
     */
    public static final int MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS = 20;

    /**
     * This is the limit imposed by SQS for the maximum number of messages that can be obtained from a single request.
     */
    public static final int MAX_NUMBER_OF_MESSAGES_FROM_SQS = 10;
}
