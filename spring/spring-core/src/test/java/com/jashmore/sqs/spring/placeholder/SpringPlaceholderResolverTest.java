package com.jashmore.sqs.spring.placeholder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class SpringPlaceholderResolverTest {
    MockEnvironment environment;

    SpringPlaceholderResolver resolver;
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        resolver = new SpringPlaceholderResolver(environment);
    }

    @Test
    void willResolvePlaceholdersFromMapping() {
        environment.withProperty("key", "value");
        assertThat(resolver.resolvePlaceholders("something ${key}")).isEqualTo("something value");
    }

    @Test
    void willThrowExceptionsIfNoMappingFound() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolvePlaceholders("something ${key}"));
    }
}