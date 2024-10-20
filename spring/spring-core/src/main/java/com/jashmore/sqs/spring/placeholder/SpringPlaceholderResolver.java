package com.jashmore.sqs.spring.placeholder;

import com.jashmore.sqs.placeholder.PlaceholderResolver;
import org.springframework.core.env.Environment;

/**
 * Implementation that replaces placeholders via the Spring Environment, e.g. application.properties files.
 */
public class SpringPlaceholderResolver implements PlaceholderResolver {

    private final Environment environment;

    public SpringPlaceholderResolver(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public String resolvePlaceholders(final String text) {
        return environment.resolveRequiredPlaceholders(text);
    }
}
