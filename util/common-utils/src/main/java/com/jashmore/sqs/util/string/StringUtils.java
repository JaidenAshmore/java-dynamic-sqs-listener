package com.jashmore.sqs.util.string;

import com.jashmore.documentation.annotations.Nullable;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StringUtils {

    /**
     * Given an identifier that may be camel case with a range of characters, it will create an identifier that only contains lowercase letters, numbers
     * and hyphens.
     *
     * <p>For example given the following inputs, the following outputs will be generated:
     * <ul>
     *     <li>MyClassNameWithMethod: my-class-name-with-method</li>
     *     <li>MySQSNameWithMethod: my-sqs-name-with-method (note that it did not create my-sqsn-ame-with-method</li>
     *     <li>MySQS-method: my-sqs-method</li>
     *     <li>MySQS-?$$-method: my-sqs-method</li>
     * </ul>
     *
     * @param identifier the identifier to transform
     * @return the identifier in hyphen case
     */
    @SuppressWarnings("checkstyle:LocalVariableName")
    public String toLowerHyphenCase(final String identifier) {
        final StringBuilder stringBuilder = new StringBuilder();

        char[] characters = identifier.toCharArray();
        boolean previousCharacterIsSeparator = false;
        boolean previousCharacterIsUpperCase = false;
        for (int i = 0; i < characters.length; ++i) {
            char c = characters[i];
            if (Character.isLetter(c)) {
                if (Character.isUpperCase(c)) {
                    if (i > 0 && !previousCharacterIsSeparator && !previousCharacterIsUpperCase) {
                        stringBuilder.append('-');
                    }
                    if (previousCharacterIsUpperCase && i + 1 < characters.length && Character.isLowerCase(characters[i + 1])) {
                        stringBuilder.append('-');
                    }
                    stringBuilder.append(Character.toLowerCase(c));
                    previousCharacterIsUpperCase = true;
                } else {
                    stringBuilder.append(c);
                    previousCharacterIsUpperCase = false;
                }
                previousCharacterIsSeparator = false;
            } else if (Character.isDigit(c)) {
                stringBuilder.append(c);
                previousCharacterIsSeparator = false;
                previousCharacterIsUpperCase = false;
            } else {
                if (!previousCharacterIsSeparator) {
                    stringBuilder.append('-');
                }
                previousCharacterIsSeparator = true;
                previousCharacterIsUpperCase = false;
            }
        }

        return stringBuilder.toString();
    }

    /**
     * Return whether the text is null or empty.
     *
     * @param text to check
     * @return whether it has content
     */
    public boolean hasText(@Nullable final String text) {
        return text != null && !text.isEmpty();
    }
}
