package com.jashmore.sqs.processor;

import static java.util.stream.Collectors.toList;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DefaultMethodParameter;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.resolver.MessageResolver;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Default implementation of the {@link MessageProcessor} that will simply resolve arguments, process the message and delete the
 * message from the queue if it was completed successfully.
 */
@ThreadSafe
@AllArgsConstructor
public class DefaultMessageProcessor implements MessageProcessor {
    private final QueueProperties queueProperties;
    private final MessageResolver messageResolver;
    private final Method messageConsumerMethod;
    private final Object messageConsumerBean;
    private final Class<?> returnType;
    private final List<InternalArgumentResolver> methodArgumentResolvers;
    private final boolean hasAcknowledgeParameter;

    public DefaultMessageProcessor(final ArgumentResolverService argumentResolverService,
                                   final QueueProperties queueProperties,
                                   final MessageResolver messageResolver,
                                   final Method messageConsumerMethod,
                                   final Object messageConsumerBean) {
        this.queueProperties = queueProperties;
        this.messageResolver = messageResolver;
        this.messageConsumerMethod = messageConsumerMethod;
        this.messageConsumerBean = messageConsumerBean;
        this.hasAcknowledgeParameter = hasAcknowledgeParameter();
        this.returnType = messageConsumerMethod.getReturnType();
        this.methodArgumentResolvers = getArgumentResolvers(argumentResolverService);
    }

    @Override
    public void processMessage(final Message message) throws MessageProcessingException {
        final Object[] arguments = getArguments(message);

        final Object result;
        try {
            result = messageConsumerMethod.invoke(messageConsumerBean, arguments);
        } catch (final InvocationTargetException | IllegalAccessException | RuntimeException exception) {
            throw new MessageProcessingException("Error processing message", exception);
        }

        if (hasAcknowledgeParameter) {
            // If the method has the Acknowledge parameter it is up to them to resolve the message
            return;
        }

        if (CompletableFuture.class.isAssignableFrom(returnType)) {
            final CompletableFuture<?> resultCompletableFuture = (CompletableFuture) result;

            if (resultCompletableFuture == null) {
                throw new MessageProcessingException("Method returns CompletableFuture but null was returned");
            }

            try {
                resultCompletableFuture
                        .thenAccept((ignored) -> messageResolver.resolveMessage(message))
                        .get();
            } catch (final InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new MessageProcessingException("Thread interrupted while processing message");
            } catch (final ExecutionException executionException) {
                throw new MessageProcessingException("Error processing message", executionException.getCause());
            }
        } else {
            messageResolver.resolveMessage(message);
        }
    }

    /**
     * Get the arguments for the method for the message that is being processed.
     *
     * @param message     the message to populate the arguments from
     * @return the array of arguments to call the method with
     */
    private Object[] getArguments(final Message message) {
        return methodArgumentResolvers.stream()
                .map(resolver -> resolver.resolveArgument(message))
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
                        return message -> (Acknowledge) () -> messageResolver.resolveMessage(message);
                    }

                    final ArgumentResolver<?> argumentResolver = argumentResolverService.getArgumentResolver(methodParameter);
                    return message -> argumentResolver.resolveArgumentForParameter(queueProperties, methodParameter, message);
                })
                .collect(toList());
    }

    private boolean hasAcknowledgeParameter() {
        return Arrays.stream(messageConsumerMethod.getParameters())
                .anyMatch(DefaultMessageProcessor::isAcknowledgeParameter);
    }

    private static boolean isAcknowledgeParameter(final Parameter parameter) {
        return Acknowledge.class.isAssignableFrom(parameter.getType());
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
         * @return the argument that should be used for the corresponding parameter
         */
        Object resolveArgument(final Message message);
    }
}
