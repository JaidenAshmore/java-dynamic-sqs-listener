package com.jashmore.sqs.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PreconditionsTest {

    @Nested
    class CheckNotNull {
        @Test
        @SuppressWarnings("ObviousNullCheck")
        void shouldNotThrowExceptionWhenNonNull() {
            Preconditions.checkNotNull("non-null", "message");
        }

        @Test
        void shouldThrowExceptionWhenNull() {
            final NullPointerException exception = assertThrows(NullPointerException.class, () -> Preconditions.checkNotNull(null, "message"));
            assertThat(exception).hasMessage("message");
        }
    }

}