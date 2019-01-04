package com.jashmore.sqs.argument;

import com.jashmore.sqs.QueueProperties;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Parameter;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Service used calculate argument values for particular parameters of a message consumer's method from a message.
 *
 * <p>For example, it may want to extract the message id of the message to be placed into a certain method parameter and this
 * service has the responsibility of doing this.
 *
 * <p>To increase testability and reduce complexity the resolution can be delegate to an individual {@link ArgumentResolver} to
 * handle one type of argument resolution. Therefore, each {@link ArgumentResolver} only needs to handle one type of argument
 * resolution greatly reducing complexity.
 *
 * <p>As there could be multiple messages all being processed at once, and therefore resolving many arguments concurrently,
 * the implementations of this class must be thread safe.
 */
@ThreadSafe
public interface ArgumentResolverService {
    /**
     * Resolve the argument value for the given parameter of the method.
     *
     * @param queueProperties details about the queue that the message came from
     * @param parameter       the parameter to get the argument value for
     * @param message         the message being processed by this queue
     * @return the value of the argument
     * @throws ArgumentResolutionException when there was an error determine the parameter argument value
     */
    Object resolveArgument(QueueProperties queueProperties, Parameter parameter, Message message) throws ArgumentResolutionException;
}
