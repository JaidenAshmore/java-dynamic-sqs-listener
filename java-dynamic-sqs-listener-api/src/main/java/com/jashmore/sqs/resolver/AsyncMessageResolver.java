package com.jashmore.sqs.resolver;

/**
 * Asynchronous implementation of the {@link MessageResolver} that relies on a background thread to perform the actual deletion of the messages.
 *
 * <p>To utilise this resolver, the class must run on a separate thread. For example:
 *
 * <pre class="code">
 *     final AsyncMessageResolver messageResolver = new SomeAsyncMessageResolverImpl(...);
 *     // start it on a background thread
 *     Future&lt;?&gt; resolverFuture = Executors.newCachedThreadPool().submit(messageResolver);
 *
 *     // Now messages can be resolved
 *     CompletableFuture&lt;?&gt; resolveMessageFuture = messageResolver.resolverMessage(message);
 *
 *     // Stop the message resolver when you are done
 *     resolverFuture.cancel(true);
 * </pre>
 */
public interface AsyncMessageResolver extends MessageResolver, Runnable {
}
