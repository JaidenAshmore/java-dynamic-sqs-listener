package com.jashmore.sqs.placeholder;

import com.jashmore.documentation.annotations.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple implementation of a {@link PlaceholderResolver} that just does a simple string
 * replace from a static map of values. This is mostly only useful for testing.
 */
public class StaticPlaceholderResolver implements PlaceholderResolver {

    private final Map<String, String> placeholderMap;

    public StaticPlaceholderResolver() {
        this.placeholderMap = new HashMap<>();
    }

    public StaticPlaceholderResolver withMapping(final String from, final String to) {
        placeholderMap.put(from, to);
        return this;
    }

    public void reset() {
        this.placeholderMap.clear();
    }

    @Override
    public String resolvePlaceholders(@Nonnull final String text) {
        String previousText = text;
        String mutatedText = null;
        while (!previousText.equals(mutatedText)) {
            mutatedText = mutatedText == null ? text : mutatedText;
            previousText = mutatedText;

            for (Map.Entry<String, String> entry : placeholderMap.entrySet()) {
                mutatedText = mutatedText.replace(entry.getKey(), entry.getValue());
            }
        }
        return mutatedText;
    }
}
