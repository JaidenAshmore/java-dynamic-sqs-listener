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
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Default implementation of the {@link MessageProcessor} that will simply resolve arguments, process the message and delete the
 * message from the queue if it was completed successfully.
 */
@ThreadSafe
public class CoreMessageProcessor implements MessageProcessor {
    private final QueueProperties queueProperties;
    private final SqsAsyncClient sqsAsyncClient;
    private final Method messageConsumerMethod;
    private final Object messageConsumerBean;

    // These are calculated in the constructor so that it is not recalculated each time a message is processed
    private final List<InternalArgumentResolver> methodArgumentResolvers;
    private final Class<?> returnType;
    private final boolean hasAcknowledgeParameter;

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
        this.returnType = messageConsumerMethod.getReturnType();
    }

    @Override
    public CompletableFuture<?> processMessage(final Message message, final Runnable resolveMessageCallback) throws MessageProcessingException {
        final Object[] arguments = getArguments(message, resolveMessageCallback);

        final Object result;
        try {
            result = messageConsumerMethod.invoke(messageConsumerBean, arguments);
        } catch (final InvocationTargetException | IllegalAccessException | RuntimeException exception) {
            return CompletableFutureUtils.completedExceptionally(new MessageProcessingException("Error processing message", exception));
        }

        if (hasAcknowledgeParameter) {
            // If the method has the Acknowledge parameter it is up to them to resolve the message
            return CompletableFuture.completedFuture(null);
        }

        if (CompletableFuture.class.isAssignableFrom(returnType)) {
            final CompletableFuture<?> resultCompletableFuture = (CompletableFuture) result;

            if (resultCompletableFuture == null) {
                return CompletableFutureUtils.completedExceptionally(new MessageProcessingException("Method returns CompletableFuture but null was returned"));
            }

            return resultCompletableFuture
                    .thenAccept((ignored) -> resolveMessageCallback.run());
        } else {
            resolveMessageCallback.run();
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Get the arguments for the method for the message that is being processed.
     *
     * @param message     the message to populate the arguments from
     * @return the array of arguments to call the method with
     */
    private Object[] getArguments(final Message message, final Runnable resolveMessageCallback) {
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
                        return (message, resolveMessageCallback) -> (Acknowledge) resolveMessageCallback::run;
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
        Object resolveArgument(final Message message, final Runnable resolveMessageCallback);
    }
}
