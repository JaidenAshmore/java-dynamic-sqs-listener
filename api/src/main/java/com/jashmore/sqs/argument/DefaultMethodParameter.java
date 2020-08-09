package com.jashmore.sqs.argument;

import com.jashmore.documentation.annotations.PositiveOrZero;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import lombok.Builder;

/**
 * Default implementation of the {@link MethodParameter}, nothing special about it.
 */
@Builder
public class DefaultMethodParameter implements MethodParameter {
    private final Method method;
    private final Parameter parameter;
    private final int parameterIndex;

    public DefaultMethodParameter(final Method method, final Parameter parameter, @PositiveOrZero final int parameterIndex) {
        this.method = method;
        this.parameter = parameter;
        this.parameterIndex = parameterIndex;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public Parameter getParameter() {
        return parameter;
    }

    @Override
    @PositiveOrZero
    public int getParameterIndex() {
        return parameterIndex;
    }
}
