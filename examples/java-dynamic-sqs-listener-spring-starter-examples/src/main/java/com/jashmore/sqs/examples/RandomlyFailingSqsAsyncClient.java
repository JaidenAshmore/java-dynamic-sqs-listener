package com.jashmore.sqs.examples;

import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;
import software.amazon.awssdk.utils.SdkAutoCloseable;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@AllArgsConstructor
public class RandomlyFailingSqsAsyncClient implements SqsAsyncClient {
    private static final Random RANDOM = new Random();

    @Delegate(excludes = { MethodsToFail.class, SdkAutoCloseable.class})
    private final SqsAsyncClient delegate;

    @Override
    public CompletableFuture<ReceiveMessageResponse> receiveMessage(final ReceiveMessageRequest receiveMessageRequest) {
//        if (RANDOM.nextBoolean()) {
//            return delegate.receiveMessage(receiveMessageRequest);
//        } else {
            final CompletableFuture<ReceiveMessageResponse> future = new CompletableFuture<>();
            future.completeExceptionally(SqsException.builder().message("Error").statusCode(403).requestId("1").build());
            return future;
//        }
    }

    @Override
    public CompletableFuture<ReceiveMessageResponse> receiveMessage(final Consumer<ReceiveMessageRequest.Builder> receiveMessageRequest) {
//        if (RANDOM.nextBoolean()) {
//            return delegate.receiveMessage(receiveMessageRequest);
//        } else {
            final CompletableFuture<ReceiveMessageResponse> future = new CompletableFuture<>();
            future.completeExceptionally(SqsException.builder().message("Error").statusCode(403).requestId("2").build());
            return future;
//        }
    }

    @SuppressWarnings("unused")
    public interface MethodsToFail {
        CompletableFuture<ReceiveMessageResponse> receiveMessage(final ReceiveMessageRequest receiveMessageRequest);
        CompletableFuture<ReceiveMessageResponse> receiveMessage(final Consumer<ReceiveMessageRequest.Builder> receiveMessageRequest);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
