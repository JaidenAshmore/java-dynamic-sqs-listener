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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.concurrent.atomic.AtomicBoolean;
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
        final Parameter[] parameters = messageConsumerMethod.getParameters();

        final AtomicBoolean hasAcknowledgeField = new AtomicBoolean();
        final Acknowledge acknowledge = () -> messageResolver.resolveMessage(message);
        final Object[] arguments = IntStream.range(0, parameters.length)
                .mapToObj(parameterIndex -> {
                    final Parameter parameter = messageConsumerMethod.getParameters()[parameterIndex];

                    if (Acknowledge.class.isAssignableFrom(parameter.getType())) {
                        hasAcknowledgeField.set(true);
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

        try {
            messageConsumerMethod.invoke(messageConsumerBean, arguments);
        } catch (final IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            throw new MessageProcessingException("Error processing message", exception);
        }

        // If the method doesn't consume the Acknowledge field, it will acknowledge the method here on success
        if (!hasAcknowledgeField.get()) {
            acknowledge.acknowledgeSuccessful();
        }
    }
}
