package com.jashmore.sqs.broker;

import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.retriever.MessageRetriever;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Broker used to co-ordinate the retrieval and processing of messages from a remote queue.
 *
 * <p>If you were to consider this library as similar to a pub-sub system, this would be considered the broker that transports messages from the publisher
 * to the subscriber.  It will request for messages from the {@link MessageRetriever} and delegate the processing to a corresponding {@link MessageProcessor}.
 *
 * <p>As this is a non-blocking class that will start the message process in a background thread there is the possibility of usage of this class in multiple
 * threads. Therefore, implementations of this class must be thread safe.
 */
@ThreadSafe
public interface MessageBroker extends Runnable {

}
