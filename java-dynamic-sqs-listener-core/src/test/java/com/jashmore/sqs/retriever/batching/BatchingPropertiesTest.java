package com.jashmore.sqs.retriever.batching;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static com.jashmore.sqs.aws.AwsConstants.MAX_NUMBER_OF_MESSAGES_FROM_SQS;
import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
import static org.hamcrest.core.Is.isA;

public class BatchingPropertiesTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void desiredMinBatchedMessagesIsRequired() {
        // arrange
        expectedException.expect(isA(NullPointerException.class));

        // act
        BatchingProperties.builder()
                .maxBatchedMessages(10)
                .maxNumberOfMessagesToObtainFromServer(10)
                .build();
    }

    @Test
    public void maxBatchedMessagesIsRequired() {
        // arrange
        expectedException.expect(isA(NullPointerException.class));

        // act
        BatchingProperties.builder()
                .desiredMinBatchedMessages(10)
                .maxNumberOfMessagesToObtainFromServer(10)
                .build();
    }

    @Test
    public void desiredMessageBatchedFailsWhenLessThanZero() {
        // arrange
        expectedException.expect(isA(IllegalArgumentException.class));
        expectedException.expectMessage("desiredMinBatchedMessages should be greater than equal to zero");

        // act
        BatchingProperties.builder()
                .desiredMinBatchedMessages(-1)
                .build();
    }

    @Test
    public void zeroDesiredMessagesBatchedIsEligible() {
        // act
        BatchingProperties.builder()
                .maxBatchedMessages(2)
                .desiredMinBatchedMessages(0)
                .build();
    }

    @Test
    public void desiredMessageBatchedFailsWhenGreaterThanMaxBatchedMessages() {
        // arrange
        expectedException.expect(isA(IllegalArgumentException.class));
        expectedException.expectMessage("maxBatchedMessages(2) should be greater than or equal to desiredMinBatchedMessages(5)");

        // act
        BatchingProperties.builder()
                .desiredMinBatchedMessages(5)
                .maxBatchedMessages(2)
                .maxNumberOfMessagesToObtainFromServer(10)
                .build();
    }

    @Test
    public void maxNumberOfMessagesToObtainFromServerShouldBeLessThanAmazonLimit() {
        // arrange
        expectedException.expect(isA(IllegalArgumentException.class));
        expectedException.expectMessage("maxNumberOfMessagesToObtainFromServer should be less than the SQS limit of 10");

        // act
        BatchingProperties.builder()
                .desiredMinBatchedMessages(2)
                .maxBatchedMessages(5)
                .maxNumberOfMessagesToObtainFromServer(MAX_NUMBER_OF_MESSAGES_FROM_SQS + 10)
                .build();
    }

    @Test
    public void maxNumberOfMessagesToObtainFromServerShouldBeGreaterThanZero() {
        // arrange
        expectedException.expect(isA(IllegalArgumentException.class));
        expectedException.expectMessage("maxNumberOfMessagesToObtainFromServer should be greater than 0");

        // act
        BatchingProperties.builder()
                .desiredMinBatchedMessages(2)
                .maxBatchedMessages(5)
                .maxNumberOfMessagesToObtainFromServer(0)
                .build();
    }

    @Test
    public void errorBackoffTimeShouldBeGreaterThanOrEqualToZero() {
        // arrange
        expectedException.expect(isA(IllegalArgumentException.class));
        expectedException.expectMessage("errorBackoffTimeInMilliseconds should be greater than or equal to zero");

        // act
        BatchingProperties.builder()
                .desiredMinBatchedMessages(2)
                .maxBatchedMessages(5)
                .errorBackoffTimeInMilliseconds(-100)
                .build();
    }

    @Test
    public void waitTimeShouldBeGreaterThanZero() {
        // arrange
        expectedException.expect(isA(IllegalArgumentException.class));
        expectedException.expectMessage("maxWaitTimeInSecondsToObtainMessagesFromServer should be greater than or equal to zero");

        // act
        BatchingProperties.builder()
                .desiredMinBatchedMessages(2)
                .maxBatchedMessages(5)
                .maxWaitTimeInSecondsToObtainMessagesFromServer(-1)
                .build();
    }

    @Test
    public void waitTimeShouldBeLessThanAmazonLimit() {
        // arrange
        expectedException.expect(isA(IllegalArgumentException.class));
        expectedException.expectMessage("maxWaitTimeInSecondsToObtainMessagesFromServer should be less than the SQS limit of 20");

        // act
        BatchingProperties.builder()
                .desiredMinBatchedMessages(2)
                .maxBatchedMessages(5)
                .maxWaitTimeInSecondsToObtainMessagesFromServer(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS + 10)
                .build();
    }
}
