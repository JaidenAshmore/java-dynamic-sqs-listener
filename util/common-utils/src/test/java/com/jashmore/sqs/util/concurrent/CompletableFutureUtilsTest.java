package com.jashmore.sqs.util.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jashmore.sqs.util.ExpectedTestException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

class CompletableFutureUtilsTest {
    @Nested
    class CompletedExceptionally {
        @Test
        void completedExceptionallyReturnsCompletableFutureThatIsCompletedExceptionally() {
            // arrange
            final RuntimeException exception = new RuntimeException("test");

            // act
            final ExecutionException exceptionThrown =
                    assertThrows(ExecutionException.class, () -> CompletableFutureUtils.completedExceptionally(exception).get());

            // assert
            assertThat(exceptionThrown.getCause()).isSameAs(exception);
        }
    }

    @Nested
    class AllOf {
        @Test
        void willOnlyResolveWhenAllFuturesResolve() {
            // arrange
            final CompletableFuture<String> futureOne = new CompletableFuture<>();
            final CompletableFuture<String> futureTwo = new CompletableFuture<>();

            // act
            final CompletableFuture<List<String>> newFuture = CompletableFutureUtils.allOf(Arrays.asList(futureOne, futureTwo));
            assertThat(newFuture).isNotCompleted();
            futureOne.complete("first");
            assertThat(newFuture).isNotCompleted();
            futureTwo.complete("second");

            // assert
            assertThat(newFuture).isCompletedWithValue(Arrays.asList("first", "second"));
        }

        @Test
        void anyRejectionWillRejectTheFuture() {
            // arrange
            final CompletableFuture<String> futureOne = new CompletableFuture<>();
            final CompletableFuture<String> futureTwo = new CompletableFuture<>();

            // act
            final CompletableFuture<List<String>> newFuture = CompletableFutureUtils.allOf(Arrays.asList(futureOne, futureTwo));
            assertThat(newFuture).isNotCompleted();
            futureOne.completeExceptionally(new ExpectedTestException());
            futureTwo.complete("second");

            // assert
            assertThat(newFuture).isCompletedExceptionally();
        }

        @Test
        void orderOfResultsWillMatchOrderOfFuturesNotCompletionTime() {
            // arrange
            final CompletableFuture<String> futureOne = new CompletableFuture<>();
            final CompletableFuture<String> futureTwo = new CompletableFuture<>();

            // act
            final CompletableFuture<List<String>> newFuture = CompletableFutureUtils.allOf(Arrays.asList(futureOne, futureTwo));
            assertThat(newFuture).isNotCompleted();
            futureTwo.complete("second");
            assertThat(newFuture).isNotCompleted();
            futureOne.complete("first");

            // assert
            assertThat(newFuture).isCompletedWithValue(Arrays.asList("first", "second"));
        }
    }
}