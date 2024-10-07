package com.jashmore.sqs.micronaut.placeholder;

import com.jashmore.sqs.placeholder.PlaceholderResolver;
import io.micronaut.context.env.Environment;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MicronautPlaceholderResolver implements PlaceholderResolver {

    private final Environment environment;

    @Override
    public String resolvePlaceholders(String text) {
        return environment.getPlaceholderResolver().resolveRequiredPlaceholders(text);
    }
}
