package com.jashmore.sqs.processor;

import static java.util.stream.Collectors.toList;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.documentation.annotations.ThreadSafe;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DefaultMethodParameter;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.processor.argument.VisibilityExtender;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Default implementation of the {@link MessageProcessor} that will simply resolve arguments, process the message and delete the
 * message from the queue if it was completed successfully.
 */
@Slf4j
@ThreadSafe
public class CoreMessageProcessor implements MessageProcessor {

    private final MessageProcessor delegate;

    public CoreMessageProcessor(
        final ArgumentResolverService argumentResolverService,
        final QueueProperties queueProperties,
        final SqsAsyncClient sqsAsyncClient,
        final Method messageConsumerMethod,
        final Object messageConsumerBean
    ) {
        final boolean hasAcknowledgeParameter = hasAcknowledgeParameter(messageConsumerMethod);
        final boolean isAsynchronous = CompletableFuture.class.isAssignableFrom(messageConsumerMethod.getReturnType());
        final ArgumentResolvers argumentResolvers = determineArgumentResolvers(
            argumentResolverService,
            queueProperties,
            messageConsumerMethod
        );

        if (isAsynchronous) {
            final Function<Object[], CompletableFuture<?>> messageExecutor = arguments -> {
                try {
                    return (CompletableFuture<?>) messageConsumerMethod.invoke(messageConsumerBean, arguments);
                } catch (IllegalAccessException exception) {
                    return CompletableFutureUtils.completedExceptionally(new MessageProcessingException(exception));
                } catch (InvocationTargetException exception) {
                    return CompletableFutureUtils.completedExceptionally(new MessageProcessingException(exception.getCause()));
                }
            };

            if (hasAcknowledgeParameter) {
                this.delegate =
                    new AsyncLambdaMessageProcessor(
                        sqsAsyncClient,
                        queueProperties,
                        (message, acknowledge, visibilityExtender) -> {
                            final Object[] arguments = argumentResolvers.resolveArgument(message, acknowledge, visibilityExtender);
                            return messageExecutor.apply(arguments);
                        }
                    );
            } else {
                this.delegate =
                    new AsyncLambdaMessageProcessor(
                        sqsAsyncClient,
                        queueProperties,
                        false,
                        (message, visibilityExtender) -> {
                            final Object[] arguments = argumentResolvers.resolveArgument(message, null, visibilityExtender);
                            return messageExecutor.apply(arguments);
                        }
                    );
            }
        } else {
            final Consumer<Object[]> messageExecutor = arguments -> {
                try {
                    messageConsumerMethod.invoke(messageConsumerBean, arguments);
                } catch (IllegalAccessException illegalAccessException) {
                    throw new MessageProcessingException(illegalAccessException);
                } catch (InvocationTargetException exception) {
                    throw new MessageProcessingException(exception.getCause());
                }
            };

            if (hasAcknowledgeParameter) {
                this.delegate =
                    new LambdaMessageProcessor(
                        sqsAsyncClient,
                        queueProperties,
                        (message, acknowledge, visibilityExtender) -> {
                            final Object[] arguments = argumentResolvers.resolveArgument(message, acknowledge, visibilityExtender);
                            messageExecutor.accept(arguments);
                        }
                    );
            } else {
                this.delegate =
                    new LambdaMessageProcessor(
                        sqsAsyncClient,
                        queueProperties,
                        false,
                        (message, visibilityExtender) -> {
                            final Object[] arguments = argumentResolvers.resolveArgument(message, null, visibilityExtender);
                            messageExecutor.accept(arguments);
                        }
                    );
            }
        }
    }

    private static ArgumentResolvers determineArgumentResolvers(
        final ArgumentResolverService argumentResolverService,
        final QueueProperties queueProperties,
        final Method method
    ) {
        final Parameter[] parameters = method.getParameters();
        List<InternalArgumentResolver> argumentResolvers = IntStream
            .range(0, parameters.length)
            .<InternalArgumentResolver>mapToObj(parameterIndex -> {
                final Parameter parameter = parameters[parameterIndex];

                final MethodParameter methodParameter = DefaultMethodParameter
                    .builder()
                    .method(method)
                    .parameter(parameter)
                    .parameterIndex(parameterIndex)
                    .build();

                if (isAcknowledgeParameter(parameter)) {
                    return (message, acknowledge, visibilityExtender) -> acknowledge;
                }

                if (isVisibilityExtenderParameter(parameter)) {
                    return (message, acknowledge, visibilityExtender) -> visibilityExtender;
                }

                final ArgumentResolver<?> argumentResolver = argumentResolverService.getArgumentResolver(methodParameter);
                return (message, acknowledge, visibilityExtender) ->
                    argumentResolver.resolveArgumentForParameter(queueProperties, methodParameter, message);
            })
            .collect(toList());

        return (message, acknowledge, visibilityExtender) ->
            argumentResolvers
                .stream()
                .map(argumentResolver -> argumentResolver.resolveArgument(message, acknowledge, visibilityExtender))
                .toArray(Object[]::new);
    }

    private static boolean hasAcknowledgeParameter(final Method method) {
        return Arrays.stream(method.getParameters()).anyMatch(CoreMessageProcessor::isAcknowledgeParameter);
    }

    private static boolean isAcknowledgeParameter(final Parameter parameter) {
        return Acknowledge.class.isAssignableFrom(parameter.getType());
    }

    private static boolean isVisibilityExtenderParameter(final Parameter parameter) {
        return VisibilityExtender.class.isAssignableFrom(parameter.getType());
    }

    @Override
    public CompletableFuture<?> processMessage(final Message message, final Supplier<CompletableFuture<?>> resolveMessageCallback) {
        return delegate.processMessage(message, resolveMessageCallback);
    }

    /**
     * Internal resolver for resolving the argument given the message.
     */
    @FunctionalInterface
    interface InternalArgumentResolver {
        Object resolveArgument(
            final Message message,
            @Nullable final Acknowledge acknowledge,
            @Nullable final VisibilityExtender visibilityExtender
        );
    }

    /**
     * Resolve all of the arguments for this message.
     */
    @FunctionalInterface
    interface ArgumentResolvers {
        Object[] resolveArgument(
            final Message message,
            @Nullable final Acknowledge acknowledge,
            @Nullable final VisibilityExtender visibilityExtender
        );
    }
}
