package com.jashmore.sqs.argument;

import com.jashmore.documentation.annotations.PositiveOrZero;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Represents details about the method parameter that needs to have the argument resolved.
 */
public interface MethodParameter {
    /**
     * Get the method that this parameter exists on.
     *
     * @return the method for the parameter
     */
    Method getMethod();

    /**
     * The parameter for the method.
     *
     * @return the parameter for the method
     */
    Parameter getParameter();

    /**
     * The index of the parameter in the method parameter list.
     *
     * @return the index of the parameter for the method parameter list
     */
    @PositiveOrZero
    int getParameterIndex();
}
