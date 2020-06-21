package com.jashmore.sqs.retriever;

import com.jashmore.documentation.annotations.ThreadSafe;
import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Class used for retrieving messages to execute from the queue.
 *
 * <p>If you were to consider this library as similar to a pub-sub system, this could be considered the publisher.  It polls for messages from the
 * remote queue which will be taken by the {@link MessageBroker}, transferred to the corresponding {@link MessageProcessor} that knows how to process
 * this message and then deleted by the {@link MessageResolver}.
 *
 * <p>As there could be multiple threads wanting to process messages the implementations of this class must be thread safe.
 */
@ThreadSafe
public interface MessageRetriever {
    /**
     * Request the retrieval of a message returning a {@link CompletableFuture} that will be resolved when the message is eventually obtained.
     *
     * @return the the future that will be resolved with the message when obtained eventually
     */
    CompletableFuture<Message> retrieveMessage();

    /**
     * Run the process that will perform the retrieval of messages from the server, this should be run on a background thread.
     *
     * <p>Implementations of this method could be polling the queue at intervals or prefetching messages that will be returned when the
     * {@link #retrieveMessage()} method is called.
     *
     * <p>This method should not exit until it is interrupted by the container that has started this method. An example of starting this is the
     * following:
     *
     * <pre class="code">
     *     final MessageRetriever messageRetriever = new SomeMessageRetrieverImpl(...);
     *     // start it on a background thread
     *     Future&lt;?&gt; retrieverFuture = Executors.newCachedThreadPool().submit(() -&gt; {
     *          List&lt;Message&gt; leftoverMessages = messageRetriever.run();
     *
     *          // do something with the leftover messages here
     *     });
     *
     *     CompletableFuture&lt;Message&gt; retrievedMessageFuture = messageRetriever.retrieveMessage();
     *     // do something with this future
     *
     *     // Stop the message retriever when you are done
     *     resolverFuture.cancel(true);
     * </pre>
     *
     * <p>When this method exits it should return a list of messages that were downloaded but may not have been processed yet. For example, a retriever that
     * prefetches the messages should return all those prefetched messages that have not yet been processed. This allows the container to decide what to
     * do with these messages, like process them before shutting down.
     *
     * <p>When this method exits, all of the {@link CompletableFuture}s for messages that have not been resolved should be cancelled as they will never be
     * resolved anymore.
     *
     * @return the messages that were downloaded but have not been taken for processing
     */
    List<Message> run();
}
