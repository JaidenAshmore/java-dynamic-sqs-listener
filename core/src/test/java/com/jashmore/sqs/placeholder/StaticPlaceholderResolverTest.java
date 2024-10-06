package com.jashmore.sqs.placeholder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StaticPlaceholderResolverTest {

    @Test
    void canReplacePlaceholdersFromStaticList() {
        assertThat(new StaticPlaceholderResolver().withMapping("from", "to").resolvePlaceholders("from something"))
            .isEqualTo("to something");
    }

    @Test
    void resettingPlaceholdersWillClearMapping() {
        final StaticPlaceholderResolver placeholderResolver = new StaticPlaceholderResolver().withMapping("from", "to");

        placeholderResolver.reset();

        assertThat(placeholderResolver.resolvePlaceholders("from something")).isEqualTo("from something");
    }

    @Test
    void canChangePlaceholderReplacements() {
        final StaticPlaceholderResolver placeholderResolver = new StaticPlaceholderResolver()
            .withMapping("first", "second")
            .withMapping("second", "third word")
            .withMapping("third", "fourth");

        assertThat(placeholderResolver.resolvePlaceholders("first for us")).isEqualTo("fourth word for us");
    }
}
