package com.jashmore.sqs.util.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

class CompletableFutureUtilsTest {
    @Test
    void completedExceptionallyReturnsCompletableFutureThatIsCompletedExceptionally() {
        // arrange
        final RuntimeException exception = new RuntimeException("test");

        // act
        final ExecutionException exceptionThrown = assertThrows(ExecutionException.class, () -> CompletableFutureUtils.completedExceptionally(exception).get());

        // assert
        assertThat(exceptionThrown.getCause()).isSameAs(exception);
    }
}