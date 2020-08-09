package com.jashmore.sqs.argument;

/**
 * Exception thrown if there is no available {@link ArgumentResolver} that is able to resolve the provided parameter for the method.
 */
public class UnsupportedArgumentResolutionException extends RuntimeException {

    public UnsupportedArgumentResolutionException() {
        super("Unable to resolve message argument");
    }

    public UnsupportedArgumentResolutionException(final MethodParameter methodParameter) {
        super(
            String.format(
                "No eligible ArgumentResolver for parameter[%d] for method: %s",
                methodParameter.getParameterIndex(),
                methodParameter.getMethod().getDeclaringClass().getName() + "#" + methodParameter.getMethod().getName()
            )
        );
    }
}
