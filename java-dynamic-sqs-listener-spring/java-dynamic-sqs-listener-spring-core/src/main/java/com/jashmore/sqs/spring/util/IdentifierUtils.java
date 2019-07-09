package com.jashmore.sqs.spring.util;

import com.google.common.base.CaseFormat;

import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

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
    public static String buildIdentifierForMethod(final String identifier, final Class<?> clazz, final Method method) {
        if (StringUtils.isEmpty(identifier.trim())) {
            return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, clazz.getSimpleName() + "-" + method.getName());
        } else {
            return identifier.trim();
        }
    }
}
