package com.jashmore.sqs.util;

import static org.hamcrest.core.IsNull.nullValue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PreconditionsTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldThrowNullPointerExceptionWhenArgumentIsNullInNullCheck() {
        // arrange
        expectedException.expect(NullPointerException.class);
        expectedException.expectMessage("Argument with name 'test' should not be null");

        // act
        Preconditions.checkArgumentNotNull(null, "test");
    }

    @Test
    public void shouldNotThrowNullPointerExceptionWhenArgumentIsNotNullInNullCheck() {
        // act
        Preconditions.checkArgumentNotNull(1, "test");
    }

    @Test
    public void shouldNotThrowNullPointerExceptionWhenArgumentIsNotNullWithNoMessaegInNullCheck() {
        // act
        Preconditions.checkArgumentNotNull(1);
    }

    @Test
    public void shouldThrowNullPointerExceptionWithNoMessageWhenArgumentIsNullInNullCheck() {
        // arrange
        expectedException.expect(NullPointerException.class);
        expectedException.expectMessage(nullValue(String.class));

        // act
        Preconditions.checkArgumentNotNull(null);
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionWhenArgumentDoesNotMatchCondition() {
        // arrange
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("failure message");

        // act
        Preconditions.checkArgument(false, "failure message");
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionWithNoMessageWhenArgumentDoesNotMatchConditionAndNoMessageIncluded() {
        // arrange
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(nullValue(String.class));

        // act
        Preconditions.checkArgument(false);
    }

    @Test
    public void shouldNotThrowIllegalArgumentExceptionWhenArgumentDoesMatchCondition() {
        // act
        Preconditions.checkArgument(true, "should not be displayed");
    }

    @Test
    public void shouldNotThrowIllegalArgumentExceptionWhenArgumentDoesMatchConditionWithNoMessage() {
        // act
        Preconditions.checkArgument(true);
    }
}
