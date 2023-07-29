package com.jashmore.sqs.processor;

import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.util.ExpectedTestException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings({ "unused", "WeakerAccess" })
public class AsynchronousMessageListenerScenarios {

    public CompletableFuture<?> methodReturningResolvedFuture() {
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<?> methodWithSuppliedFuture(CompletableFuture<?> future) {
        return future;
    }

    public CompletableFuture<?> methodReturnFutureSubsequentlyResolved() {
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException interruptedException) {
                // ignore
            }
        });
    }

    public CompletableFuture<?> methodReturnFutureSubsequentlyRejected() {
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException interruptedException) {
                // ignore
            }
            throw new ExpectedTestException();
        });
    }

    public CompletableFuture<?> methodThatThrowsException() {
        throw new ExpectedTestException();
    }

    public CompletableFuture<?> methodThatReturnsNull() {
        return null;
    }

    public CompletableFuture<?> methodWithAcknowledge(Acknowledge acknowledge) {
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<?> methodThatCallsAcknowledgeField(Acknowledge acknowledge) {
        return acknowledge.acknowledgeSuccessful();
    }

    public static Method getMethod(final String methodName, final Class<?>... parameterClasses) {
        try {
            return AsynchronousMessageListenerScenarios.class.getDeclaredMethod(methodName, parameterClasses);
        } catch (final NoSuchMethodException exception) {
            throw new RuntimeException("Unable to find method for testing against", exception);
        }
    }
}
