package com.jashmore.sqs.retriever;

/**
 * {@link MessageRetriever} that obtains messages in the background for consumption.
 *
 * <p>For example, this could be used when messages are pulled from the queue and cached locally to improve performance by reducing the number
 * of times and latency of pulling messages from the queue.
 *
 * <p>To utilise this retriever, the class must run on a separate thread. For example:
 *
 * <pre class="code">
 *     final AsyncMessageRetriever messageRetriever = new SomeAsyncMessageRetrieverImpl(...);
 *     // start it on a background thread
 *     Future&lt;?&gt; retrieverFuture = Executors.newCachedThreadPool().submit(messageRetriever);
 *
 *     // Now messages can be retrieved
 *     Message message = retrieverFuture.retrieveMessage(message);
 *
 *     // Stop the message retriever when you are done
 *     retrieverFuture.cancel(true);
 * </pre>
 */
public interface AsyncMessageRetriever extends MessageRetriever, Runnable {

}
