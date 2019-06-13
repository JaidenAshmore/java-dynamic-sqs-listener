package com.jashmore.sqs.spring.util;

import com.google.common.base.CaseFormat;

import lombok.experimental.UtilityClass;

import java.lang.reflect.Method;

@UtilityClass
public class IdentifierUtils {
    /**
     * Build an identifier from a classes method that can be used to identify a message listener.
     *
     * @param clazz  the class that the method is on
     * @param method the method used for the message listener
     * @return a default identifier for this method
     */
    public static String buildIdentifierForMethod(final Class<?> clazz, final Method method) {
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, clazz.getSimpleName() + "-" + method.getName());
    }
}
