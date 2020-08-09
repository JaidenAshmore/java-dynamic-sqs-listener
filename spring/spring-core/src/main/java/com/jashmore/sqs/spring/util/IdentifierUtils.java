package com.jashmore.sqs.spring.util;

import static com.jashmore.sqs.util.string.StringUtils.toLowerHyphenCase;

import java.lang.reflect.Method;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

@UtilityClass
public class IdentifierUtils {

    /**
     * Builds an identifier from a provided identifier if it is not empty, otherwise build an identifier from the class and method.
     *
     * @param identifier the identifier to use if it is not an empty string
     * @param clazz      the class that the method is on, used if no identifier supplied
     * @param method     the method used for the message listener,  used if no identifier supplied
     * @return an identifier for this class' method
     */
    public String buildIdentifierForMethod(final String identifier, final Class<?> clazz, final Method method) {
        if (StringUtils.isEmpty(identifier.trim())) {
            return toLowerHyphenCase(clazz.getSimpleName() + "-" + method.getName());
        } else {
            return identifier.trim();
        }
    }
}
