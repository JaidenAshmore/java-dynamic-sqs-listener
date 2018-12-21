package com.jashmore.sqs.retriever.prefetch;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
import static org.hamcrest.core.Is.isA;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PrefetchingPropertiesTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void desiredMinPrefetchedMessagesIsRequired() {
        // arrange
        expectedException.expect(isA(NullPointerException.class));

        // act
        PrefetchingProperties.builder()
                .maxPrefetchedMessages(10)
                .build();
    }

    @Test
    public void maxPrefetchedMessagesIsRequired() {
        // arrange
        expectedException.expect(isA(NullPointerException.class));

        // act
        PrefetchingProperties.builder()
                .desiredMinPrefetchedMessages(10)
                .build();
    }

    @Test
    public void desiredMinMessagePrefetchedMessagesFailsWhenLessThanZero() {
        // arrange
        expectedException.expect(isA(IllegalArgumentException.class));
        expectedException.expectMessage("desiredMinPrefetchedMessages should be greater than equal to zero");

        // act
        PrefetchingProperties.builder()
                .desiredMinPrefetchedMessages(-1)
                .build();
    }

    @Test
    public void zeroDesiredMinMessagePrefetchedMessagesIsEligibleValue() {
        // act
        PrefetchingProperties.builder()
                .maxPrefetchedMessages(2)
                .desiredMinPrefetchedMessages(0)
                .build();
    }

    @Test
    public void desiredMinPrefetchedMessagesFailsWhenGreaterThanMaxPrefetchedMessages() {
        // arrange
        expectedException.expect(isA(IllegalArgumentException.class));
        expectedException.expectMessage("maxPrefetchedMessages(2) should be greater than or equal to desiredMinPrefetchedMessages(5)");

        // act
        PrefetchingProperties.builder()
                .desiredMinPrefetchedMessages(5)
                .maxPrefetchedMessages(2)
                .build();
    }

    @Test
    public void errorBackoffTimeShouldBeGreaterThanOrEqualToZero() {
        // arrange
        expectedException.expect(isA(IllegalArgumentException.class));
        expectedException.expectMessage("errorBackoffTimeInMilliseconds should be greater than or equal to zero");

        // act
        PrefetchingProperties.builder()
                .desiredMinPrefetchedMessages(2)
                .maxPrefetchedMessages(5)
                .errorBackoffTimeInMilliseconds(-100)
                .build();
    }

    @Test
    public void waitTimeShouldBeGreaterThanZero() {
        // arrange
        expectedException.expect(isA(IllegalArgumentException.class));
        expectedException.expectMessage("maxWaitTimeInSecondsToObtainMessagesFromServer should be greater than or equal to zero");

        // act
        PrefetchingProperties.builder()
                .desiredMinPrefetchedMessages(2)
                .maxPrefetchedMessages(5)
                .maxWaitTimeInSecondsToObtainMessagesFromServer(-1)
                .build();
    }

    @Test
    public void waitTimeShouldBeLessThanAmazonLimit() {
        // arrange
        expectedException.expect(isA(IllegalArgumentException.class));
        expectedException.expectMessage("maxWaitTimeInSecondsToObtainMessagesFromServer should be less than the SQS limit of 20");

        // act
        PrefetchingProperties.builder()
                .desiredMinPrefetchedMessages(2)
                .maxPrefetchedMessages(5)
                .maxWaitTimeInSecondsToObtainMessagesFromServer(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS + 10)
                .build();
    }
}
