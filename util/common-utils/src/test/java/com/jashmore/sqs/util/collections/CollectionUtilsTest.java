package com.jashmore.sqs.util.collections;

import static com.jashmore.sqs.util.collections.CollectionUtils.immutableListFrom;
import static com.jashmore.sqs.util.collections.CollectionUtils.immutableListOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CollectionUtilsTest {

    @SuppressWarnings("ConstantConditions")
    @Nested
    class ImmutableList {

        @Test
        void singleElementWillCreateListWithElement() {
            assertThat(immutableListOf("one")).containsExactly("one");
        }

        @Test
        void singleElementWillBeImmutable() {
            assertThrows(UnsupportedOperationException.class, () -> immutableListOf("one").add("new"));
        }

        @Test
        void twoElementsWillCreateListWithElements() {
            assertThat(immutableListOf("one", "two")).containsExactly("one", "two");
        }

        @Test
        void twoElementsWillBeImmutable() {
            assertThrows(UnsupportedOperationException.class, () -> immutableListOf("one", "two").add("new"));
        }

        @Test
        void threeElementsWillCreateListWithElements() {
            assertThat(immutableListOf("one", "two", "three")).containsExactly("one", "two", "three");
        }

        @Test
        void threeElementsWillBeImmutable() {
            assertThrows(UnsupportedOperationException.class, () -> immutableListOf("one", "two", "three").add("new"));
        }

        @Test
        void fourElementsWillCreateListWithElements() {
            assertThat(immutableListOf("one", "two", "three", "four")).containsExactly("one", "two", "three", "four");
        }

        @Test
        void fourElementsWillBeImmutable() {
            assertThrows(UnsupportedOperationException.class, () -> immutableListOf("one", "two", "three", "four").add("new"));
        }

        @Test
        void fiveElementsWillCreateListWithElements() {
            assertThat(immutableListOf("one", "two", "three", "four", "five")).containsExactly("one", "two", "three", "four", "five");
        }

        @Test
        void fiveElementsWillBeImmutable() {
            assertThrows(UnsupportedOperationException.class, () -> immutableListOf("one", "two", "three", "four", "five").add("new"));
        }

        @Test
        void multipleListsCanBeMergedTogetherToImmutableList() {
            assertThat(immutableListFrom(Arrays.asList("one", "two"), Arrays.asList("three", "four", "five")))
                .containsExactly("one", "two", "three", "four", "five");
        }

        @Test
        void multipleListsWillBeImmutable() {
            assertThrows(
                UnsupportedOperationException.class,
                () -> immutableListFrom(Arrays.asList("one", "two"), Arrays.asList("three", "four", "five")).add("new")
            );
        }
    }
}
