package com.jashmore.sqs.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImmutablesTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void immutableMapShouldContainPairPassedIn() {
        // act
        final Map<String, String> map = Immutables.immutableMap("key", "value");

        // assert
        assertThat(map).containsEntry("key", "value");
    }

    @Test
    public void immutableMapShouldThrowExceptionWhenMutating() {
        // arrange
        final Map<String, String> map = Immutables.immutableMap("key", "value");
        expectedException.expect(UnsupportedOperationException.class);

        // act
        map.put("should", "fail");
    }

    @Test
    public void immutableListShouldContainAllItems() {
        // act
        final List<String> immutableList = Immutables.immutableList("1", "2", "3");

        // assert
        assertThat(immutableList).containsExactly("1", "2", "3");
    }

    @Test
    public void immutableListShouldNotBeAbleToBeMutated() {
        // arrange
        final List<String> immutableList = Immutables.immutableList("1", "2", "3");
        expectedException.expect(UnsupportedOperationException.class);

        // assert
        immutableList.add("should fail");
    }

    @Test
    public void immutableSetShouldContainAllItems() {
        // act
        final Set<String> immutableSet = Immutables.immutableSet("1", "1", "2");

        // assert
        assertThat(immutableSet).containsExactly("1", "2");
    }

    @Test
    public void immutableSetShouldNotBeAbleToBeMutated() {
        // arrange
        final Set<String> immutableSet = Immutables.immutableSet("1", "2", "3");
        expectedException.expect(UnsupportedOperationException.class);

        // assert
        immutableSet.add("should fail");
    }
}
