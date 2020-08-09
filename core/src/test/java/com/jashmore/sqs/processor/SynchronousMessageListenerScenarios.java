package com.jashmore.sqs.processor;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.processor.argument.Acknowledge;
import com.jashmore.sqs.processor.argument.VisibilityExtender;
import com.jashmore.sqs.util.ExpectedTestException;
import java.lang.reflect.Method;

@SuppressWarnings({ "unused", "WeakerAccess" })
public class SynchronousMessageListenerScenarios {

    public void methodWithNoArguments() {
        sleep();
    }

    public void methodWithArguments(@Payload String payload, @Payload String payloadTwo) {
        sleep();
    }

    public void methodThatThrowsException() {
        throw new ExpectedTestException();
    }

    public void methodThatCallsAcknowledgeField(Acknowledge acknowledge) {
        acknowledge.acknowledgeSuccessful();
    }

    public void methodWithAcknowledge(Acknowledge acknowledge) {
        sleep();
    }

    public void methodWithVisibilityExtender(VisibilityExtender visibilityExtender) {
        sleep();
    }

    private void privateMethod() {
        sleep();
    }

    public static Method getMethod(final String methodName, final Class<?>... parameterClasses) {
        try {
            return SynchronousMessageListenerScenarios.class.getDeclaredMethod(methodName, parameterClasses);
        } catch (final NoSuchMethodException exception) {
            throw new RuntimeException("Unable to find method for testing against", exception);
        }
    }

    public void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException interruptedException) {
            throw new RuntimeException(interruptedException);
        }
    }
}
