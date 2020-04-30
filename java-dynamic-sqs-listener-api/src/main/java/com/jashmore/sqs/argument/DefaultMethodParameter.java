package com.jashmore.sqs.argument;

import com.google.common.base.Preconditions;

import lombok.Builder;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import javax.annotation.Nonnull;
import javax.validation.constraints.PositiveOrZero;

/**
 * Default implementation of the {@link MethodParameter}, nothing special about it.
 */
@Builder
public class DefaultMethodParameter implements MethodParameter {
    private final Method method;
    private final Parameter parameter;
    private final int parameterIndex;

    public DefaultMethodParameter(@Nonnull final Method method, @Nonnull final Parameter parameter, @PositiveOrZero final int parameterIndex) {
        Preconditions.checkNotNull(method, "method should not be null");
        Preconditions.checkNotNull(parameter, "parameter should not be null");
        Preconditions.checkArgument(parameterIndex >= 0, "parameterIndex must be great than or equal to zero");

        this.method = method;
        this.parameter = parameter;
        this.parameterIndex = parameterIndex;
    }

    @Nonnull
    @Override
    public Method getMethod() {
        return method;
    }

    @Nonnull
    @Override
    public Parameter getParameter() {
        return parameter;
    }

    @PositiveOrZero
    @Override
    public int getParameterIndex() {
        return parameterIndex;
    }
}
