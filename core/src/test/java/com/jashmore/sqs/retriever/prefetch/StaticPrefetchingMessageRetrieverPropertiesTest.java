package com.jashmore.sqs.retriever.prefetch;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StaticPrefetchingMessageRetrieverPropertiesTest {

    @Test
    void maxPrefetchedMessagesIsRequired() {
        assertThrows(
            NullPointerException.class,
            () -> StaticPrefetchingMessageRetrieverProperties.builder().desiredMinPrefetchedMessages(10).build()
        );
    }

    @Test
    void desiredMinMessagePrefetchedMessagesFailsWhenLessThanZero() {
        assertThrows(
            NullPointerException.class,
            () -> StaticPrefetchingMessageRetrieverProperties.builder().desiredMinPrefetchedMessages(-1).build()
        );
    }
}
