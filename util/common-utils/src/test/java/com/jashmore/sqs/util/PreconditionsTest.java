package com.jashmore.sqs.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
            final NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> Preconditions.checkNotNull(null, "message")
            );
            assertThat(exception).hasMessage("message");
        }
    }

    @Nested
    class CheckPositiveOrZero {

        @Test
        void shouldThrowIllegalArgumentExceptionWhenNegative() {
            final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Preconditions.checkPositiveOrZero(-1, "message")
            );
            assertThat(exception).hasMessage("message");
        }

        @Test
        void shouldNotThrowExceptionWhenZero() {
            Preconditions.checkPositiveOrZero(0, "message");
        }

        @Test
        void shouldNotThrowExceptionWhenPositive() {
            Preconditions.checkPositiveOrZero(1, "message");
        }
    }

    @Nested
    class CheckArgument {

        @Test
        void shouldThrowIllegalArgumentExceptionWhenExpressionIsFalse() {
            final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Preconditions.checkArgument(false, "message")
            );
            assertThat(exception).hasMessage("message");
        }

        @Test
        void shouldNotThrowExceptionWhenTrueExpression() {
            Preconditions.checkArgument(true, "message");
        }
    }
}
