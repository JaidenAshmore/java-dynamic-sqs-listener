package com.jashmore.sqs.processor;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DefaultMethodParameter;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.resolver.MessageResolver;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
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
    private final ArgumentResolverService argumentResolverService;
    private final QueueProperties queueProperties;
    private final MessageResolver messageResolver;
    private final Method messageConsumerMethod;
    private final Object messageConsumerBean;

    @Override
    public void processMessage(final Message message) throws MessageProcessingException {
        final Acknowledge acknowledge = () -> messageResolver.resolveMessage(message);
        final Object[] arguments = getArguments(acknowledge, message);

        final Object result;
        try {
            result = messageConsumerMethod.invoke(messageConsumerBean, arguments);
        } catch (final Throwable throwable) {
            throw new MessageProcessingException("Error processing message", throwable);
        }

        if (hasAcknowledgeParameter()) {
            // If the method has the Acknowledge parameter it is up to them to resolve the message
            return;
        }

        final Class<?> returnType = messageConsumerMethod.getReturnType();
        if (CompletableFuture.class.isAssignableFrom(returnType)) {
            final CompletableFuture<?> resultCompletableFuture = (CompletableFuture) result;

            if (resultCompletableFuture == null) {
                throw new MessageProcessingException("Method returns CompletableFuture but null was returned");
            }

            try {
                resultCompletableFuture
                        .thenAccept((ignored) -> acknowledge.acknowledgeSuccessful())
                        .get();
            } catch (final InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new MessageProcessingException("Thread interrupted while processing message");
            } catch (final ExecutionException executionException) {
                throw new MessageProcessingException("Error processing message", executionException.getCause());
            }
        } else {
            acknowledge.acknowledgeSuccessful();
        }
    }

    private boolean hasAcknowledgeParameter() {
        return Arrays.stream(messageConsumerMethod.getParameters())
                .anyMatch(DefaultMessageProcessor::isAcknowledgeParameter);
    }

    private static boolean isAcknowledgeParameter(final Parameter parameter) {
        return Acknowledge.class.isAssignableFrom(parameter.getType());
    }

    /**
     * Get the arguments for the method for the message that is being processed.
     *
     * @param acknowledge the acknowledge object that should be used if a parameter is an {@link Acknowledge}
     * @param message     the message to populate the arguments from
     * @return the array of arguments to call the method with
     */
    private Object[] getArguments(final Acknowledge acknowledge, final Message message) {
        final Parameter[] parameters = messageConsumerMethod.getParameters();
        return IntStream.range(0, parameters.length)
                .mapToObj(parameterIndex -> {
                    final Parameter parameter = parameters[parameterIndex];

                    if (isAcknowledgeParameter(parameter)) {
                        return acknowledge;
                    }

                    final MethodParameter methodParameter = DefaultMethodParameter.builder()
                            .method(messageConsumerMethod)
                            .parameter(parameter)
                            .parameterIndex(parameterIndex)
                            .build();

                    try {
                        return argumentResolverService.resolveArgument(queueProperties, methodParameter, message);
                    } catch (final ArgumentResolutionException argumentResolutionException) {
                        throw new MessageProcessingException("Error resolving arguments for message", argumentResolutionException);
                    }
                })
                .toArray(Object[]::new);

    }
}
