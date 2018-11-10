package com.jashmore.sqs.argument;

import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.MessageProcessor;

import java.lang.reflect.Parameter;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Used to determine if it can fulfill resolving a method parameter from the content of a message.
 *
 * <p>If you were to consider this library as similar to a pub-sub system, this could be considered the bus.  It polls for messages from the
 * remote queue and will pass them to the {@link MessageBroker} which will delegate it to the {@link MessageProcessor} that knows how to process
 * this message.
 *
 * <p>As there could be multiple messages all being processed at once, and therefore resolving many arguments concurrently,
 * the implementations of this class must be thread safe.
 */
@ThreadSafe
public interface ArgumentResolver {
    /**
     * Determine whether the provided parameter is able to be resolved by this resolver.
     *
     * @param parameter the parameter of the listener
     * @return whether this parameter value can be obtained from this resolver
     */
    boolean canResolveParameter(Parameter parameter);

    /**
     * Resolve the argument for the given parameter.
     *
     * @param queueProperties details about the queue that the arguments will be resolved for
     * @param parameter       the parameter to get the argument value for
     * @param message         the message being processed by this queue
     * @return the value of the argument
     * @throws ArgumentResolutionException when there was an error determine the parameter argument value
     */
    Object resolveArgumentForParameter(QueueProperties queueProperties, Parameter parameter, Message message) throws ArgumentResolutionException;
}
