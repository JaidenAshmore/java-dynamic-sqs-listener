package com.jashmore.sqs.examples.latency;

import static com.jashmore.sqs.examples.ExampleConstants.MESSAGE_RETRIEVAL_LATENCY_IN_MS;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class LatencyAppliedAmazonSqsAsync implements AmazonSQSAsync {
    @Delegate(excludes = MethodsToOverride.class)
    private final AmazonSQSAsync delegate;

    @Override
    public ReceiveMessageResult receiveMessage(ReceiveMessageRequest request) {
        try {
            // Let's pretend there is some latency
            Thread.sleep(MESSAGE_RETRIEVAL_LATENCY_IN_MS);
        } catch (InterruptedException interruptedException) {
            // ignore, won't happen for testing
        }

        return delegate.receiveMessage(request);
    }


    /**
     * Needed for {@link Delegate} to allow overriding.
     */
    public interface MethodsToOverride {
        ReceiveMessageResult receiveMessage(ReceiveMessageRequest request);
    }
}
