package com.jashmore.sqs.examples.latency;

import static com.jashmore.sqs.examples.ExampleConstants.MESSAGE_RETRIEVAL_LATENCY_IN_MS;

import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.utils.SdkAutoCloseable;

import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class LatencyAppliedSqsAsyncClient implements SqsAsyncClient {
    @Delegate(excludes = { MethodsToOverride.class, SdkAutoCloseable.class })
    private final SqsAsyncClient delegate;

    @Override
    public CompletableFuture<ReceiveMessageResponse> receiveMessage(final ReceiveMessageRequest receiveMessageRequest) {
        try {
            // Let's pretend there is some latency
            Thread.sleep(MESSAGE_RETRIEVAL_LATENCY_IN_MS);
        } catch (InterruptedException interruptedException) {
            // ignore, won't happen for testing
        }

        return delegate.receiveMessage(receiveMessageRequest);
    }

    @Override
    public void close() {

    }


    /**
     * Needed for {@link Delegate} to allow overriding.
     */
    public interface MethodsToOverride {
        CompletableFuture<ReceiveMessageResponse> receiveMessage(ReceiveMessageRequest receiveMessageRequest);
    }
}

