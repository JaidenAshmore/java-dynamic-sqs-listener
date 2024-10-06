package com.jashmore.sqs.spring.placeholder;

import com.jashmore.sqs.placeholder.PlaceholderResolver;
import org.springframework.core.env.Environment;

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
