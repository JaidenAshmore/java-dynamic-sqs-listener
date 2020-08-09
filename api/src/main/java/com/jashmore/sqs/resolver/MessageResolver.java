package com.jashmore.sqs.resolver;

import com.jashmore.documentation.annotations.ThreadSafe;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Will resolve the messages by deleting them from the SQS queue so that it does not get processed again.
 *
 * <p>This can internally figure out how the messages will be resolved, for example whether the message is deleted straight away or if it waits for a certain
 * period before deleting the message from the queue.
 */
@ThreadSafe
public interface MessageResolver {
    /**
     * Resolve the message by deleting it from the SQS queue.
     *
     * @param message the message to resolve
     * @return a {@link CompletableFuture} that will be completed when the message has been successfully deleted
     */
    CompletableFuture<?> resolveMessage(Message message);

    /**
     * Run the process that will actually perform the resolving of messages, this should be run on a background thread.
     *
     * <p>This method should not exit until it is interrupted by the container that has started this method. When the thread is interrupted the
     * thread should make sure any unresolved messages are resolved and it should not exit until these are completed.
     *
     * <p>An example of starting this is the following:
     *
     * <pre class="code">
     *     final MessageResolver messageResolver = new SomeMessageResolverImpl(...);
     *     // start it on a background thread
     *     Future&lt;?&gt; resolverFuture = Executors.newCachedThreadPool().submit(messageResolver);
     *
     *     // Now messages can be resolved
     *     messageResolver.resolveMessage(message)
     *         .thenAccept((response) -&gt; log.info("Message resolved with id: {}", message.id());
     *
     *     // Stop the message resolver when you are done
     *     resolverFuture.cancel(true);
     * </pre>
     */
    void run();
}
