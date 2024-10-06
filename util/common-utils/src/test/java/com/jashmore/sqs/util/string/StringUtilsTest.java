package com.jashmore.sqs.util.string;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StringUtilsTest {

    @Nested
    class ToLowerHyphenCase {

        @Test
        void willConvertCamelCaseToHyphenCase() {
            assertThat(StringUtils.toLowerHyphenCase("MyClassNameWithMethod")).isEqualTo("my-class-name-with-method");
        }

        @Test
        void acronymsWillBeReducedToSingleLowerCaseWork() {
            assertThat(StringUtils.toLowerHyphenCase("MySQSNameWithMethod")).isEqualTo("my-sqs-name-with-method");
        }

        @Test
        void acronymsWillBeCorrectlyBuiltWhenNextCharacterIsNonLetter() {
            assertThat(StringUtils.toLowerHyphenCase("MySQS-method")).isEqualTo("my-sqs-method");
        }

        @Test
        void multipleNonAlphanumericWillBeConvertedToSingleHyphen() {
            assertThat(StringUtils.toLowerHyphenCase("MySQS-?$$-method")).isEqualTo("my-sqs-method");
        }

        @Test
        void numbersWillBeMaintained() {
            assertThat(StringUtils.toLowerHyphenCase("MyMethod2WillBeSomething")).isEqualTo("my-method2-will-be-something");
        }

        @Test
        void nonUppercaseStartWillCreateMethodCorrectly() {
            assertThat(StringUtils.toLowerHyphenCase("myMethodName")).isEqualTo("my-method-name");
        }
    }

    @Nested
    class HasText {
        @Test
        void willReturnFalseWhenNull() {
            assertThat(StringUtils.hasText(null)).isFalse();
        }

        @Test
        void willReturnFalseWhenEmptyString() {
            assertThat(StringUtils.hasText("")).isFalse();
        }

        @Test
        void willReturnTrueWhenStringHasContent() {
            assertThat(StringUtils.hasText("Hello World")).isTrue();
        }
    }
}
