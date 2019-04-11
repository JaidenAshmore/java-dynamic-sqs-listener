package com.jashmore.sqs.retriever.prefetch;

import static org.hamcrest.core.Is.isA;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class StaticPrefetchingMessageRetrieverPropertiesTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void maxPrefetchedMessagesIsRequired() {
        // arrange
        expectedException.expect(isA(NullPointerException.class));
        expectedException.expectMessage("maxPrefetchedMessages");

        // act
        StaticPrefetchingMessageRetrieverProperties.builder()
                .desiredMinPrefetchedMessages(10)
                .build();
    }

    @Test
    public void desiredMinMessagePrefetchedMessagesIsRequiredField() {
        // arrange
        expectedException.expect(isA(NullPointerException.class));
        expectedException.expectMessage("desiredMinPrefetchedMessages");

        // act
        StaticPrefetchingMessageRetrieverProperties.builder()
                .maxPrefetchedMessages(1)
                .build();
    }

    @Test
    public void canBuildPropertiesWithOnlyPrefetchingMinAndMax() {
        // act
        StaticPrefetchingMessageRetrieverProperties.builder()
                .desiredMinPrefetchedMessages(5)
                .maxPrefetchedMessages(10)
                .build();
    }
}
