package com.jashmore.sqs.placeholder;

@FunctionalInterface
public interface PlaceholderResolver {
    /**
     * Resolve placeholders in the provided text. E.g. ${something.url} may be resolved to https://localhost:9090.
     *
     * @param text the text to parse and replace
     * @return the new text with placeholders replaced
     */
    String resolvePlaceholders(String text);
}
