package com.jashmore.sqs.processor;

import static java.util.stream.Collectors.toList;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DefaultMethodParameter;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.argument.visibility.DefaultVisibilityExtender;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.processor.argument.VisibilityExtender;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Default implementation of the {@link MessageProcessor} that will simply resolve arguments, process the message and delete the
 * message from the queue if it was completed successfully.
 */
@Slf4j
@ThreadSafe
public class CoreMessageProcessor implements MessageProcessor {
    private final QueueProperties queueProperties;
    private final SqsAsyncClient sqsAsyncClient;
    private final Method messageConsumerMethod;
    private final Object messageConsumerBean;

    // These are calculated in the constructor so that it is not recalculated each time a message is processed
    private final List<InternalArgumentResolver> methodArgumentResolvers;
    private final boolean hasAcknowledgeParameter;
    private final boolean returnsCompletableFuture;

    public CoreMessageProcessor(final ArgumentResolverService argumentResolverService,
                                final QueueProperties queueProperties,
                                final SqsAsyncClient sqsAsyncClient,
                                final Method messageConsumerMethod,
                                final Object messageConsumerBean) {
        this.queueProperties = queueProperties;
        this.sqsAsyncClient = sqsAsyncClient;
        this.messageConsumerMethod = messageConsumerMethod;
        this.messageConsumerBean = messageConsumerBean;

        this.methodArgumentResolvers = getArgumentResolvers(argumentResolverService);
        this.hasAcknowledgeParameter = hasAcknowledgeParameter();
        this.returnsCompletableFuture = CompletableFuture.class.isAssignableFrom(messageConsumerMethod.getReturnType());
    }

    @Override
    public CompletableFuture<?> processMessage(final Message message, final Supplier<CompletableFuture<?>> resolveMessageCallback) {
        final Object[] arguments;
        try {
            arguments = getArguments(message, resolveMessageCallback);
        } catch (RuntimeException runtimeException) {
            throw new MessageProcessingException("Error building arguments for the message listener", runtimeException);
        }

        final Object result;
        try {
            result = messageConsumerMethod.invoke(messageConsumerBean, arguments);
        } catch (IllegalAccessException exception) {
            throw new MessageProcessingException(exception);
        } catch (InvocationTargetException exception) {
            if (returnsCompletableFuture) {
                // as this is an asynchronous message listener we would say this failed to supply the message
                throw new MessageProcessingException("Message listener threw exception before returning CompletableFuture", exception.getCause());
            } else {
                return CompletableFutureUtils.completedExceptionally(new MessageProcessingException(exception.getCause()));
            }
        }

        if (hasAcknowledgeParameter) {
            // If the method has the Acknowledge parameter it is up to them to resolve the message
            return CompletableFuture.completedFuture(null);
        }

        final CompletableFuture<?> resultCompletableFuture;
        if (returnsCompletableFuture) {
            if (result == null) {
                return CompletableFutureUtils.completedExceptionally(new MessageProcessingException("Method returns CompletableFuture but null was returned"));
            }
            resultCompletableFuture = (CompletableFuture<?>) result;
        } else {
            resultCompletableFuture = CompletableFuture.completedFuture(null);
        }

        final Supplier<CompletableFuture<Object>> resolveCallbackLoggingErrorsOnly = () -> {
            try {
                return resolveMessageCallback.get()
                        .handle((i, throwable) -> {
                            if (throwable != null) {
                                log.error("Error resolving successfully processed message", throwable);
                            }
                            return null;
                        });
            } catch (RuntimeException runtimeException) {
                log.error("Failed to trigger message resolving", runtimeException);
                return CompletableFuture.completedFuture(null);
            }
        };

        return resultCompletableFuture
                .thenCompose((ignored) -> resolveCallbackLoggingErrorsOnly.get());
    }

    /**
     * Get the arguments for the method for the message that is being processed.
     *
     * @param message     the message to populate the arguments from
     * @return the array of arguments to call the method with
     */
    private Object[] getArguments(final Message message, final  Supplier<CompletableFuture<?>> resolveMessageCallback) {
        return methodArgumentResolvers.stream()
                .map(resolver -> resolver.resolveArgument(message, resolveMessageCallback))
                .toArray(Object[]::new);
    }

    private List<InternalArgumentResolver> getArgumentResolvers(final ArgumentResolverService argumentResolverService) {
        final Parameter[] parameters = messageConsumerMethod.getParameters();
        return IntStream.range(0, parameters.length)
                .<InternalArgumentResolver>mapToObj(parameterIndex -> {
                    final Parameter parameter = parameters[parameterIndex];

                    final MethodParameter methodParameter = DefaultMethodParameter.builder()
                            .method(messageConsumerMethod)
                            .parameter(parameter)
                            .parameterIndex(parameterIndex)
                            .build();

                    if (isAcknowledgeParameter(parameter)) {
                        return (message, resolveMessageCallback) -> (Acknowledge) resolveMessageCallback::get;
                    }

                    if (isVisibilityExtenderParameter(parameter)) {
                        return (message, resolveMessageCallback) -> new DefaultVisibilityExtender(sqsAsyncClient, queueProperties, message);
                    }

                    final ArgumentResolver<?> argumentResolver = argumentResolverService.getArgumentResolver(methodParameter);
                    return (message, resolveMessageCallback) -> argumentResolver.resolveArgumentForParameter(queueProperties, methodParameter, message);
                })
                .collect(toList());
    }

    private boolean hasAcknowledgeParameter() {
        return Arrays.stream(messageConsumerMethod.getParameters())
                .anyMatch(CoreMessageProcessor::isAcknowledgeParameter);
    }

    private static boolean isAcknowledgeParameter(final Parameter parameter) {
        return Acknowledge.class.isAssignableFrom(parameter.getType());
    }

    private static boolean isVisibilityExtenderParameter(final Parameter parameter) {
        return VisibilityExtender.class.isAssignableFrom(parameter.getType());
    }

    /**
     * Internal resolver for resolving the argument given the message.
     */
    @FunctionalInterface
    interface InternalArgumentResolver {
        /**
         * Resolve the argument of the method.
         *
         * @param message the message that is being processed
         * @param resolveMessageCallback the callback that should be executed when the message has successfully been processed
         * @return the argument that should be used for the corresponding parameter
         */
        Object resolveArgument(final Message message, final Supplier<CompletableFuture<?>> resolveMessageCallback);
    }
}
