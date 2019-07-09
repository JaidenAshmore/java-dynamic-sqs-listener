package com.jashmore.sqs.util.concurrent;

import org.hamcrest.core.Is;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.ExecutionException;

public class CompletableFutureUtilsTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void completedExceptionallyReturnsCompletableFutureThatIsCompletedExceptionally() throws Exception {
        // arrange
        final RuntimeException exception = new RuntimeException("test");
        expectedException.expect(ExecutionException.class);
        expectedException.expectCause(Is.is(exception));

        // act
        CompletableFutureUtils.completedExceptionally(exception).get();
    }
}