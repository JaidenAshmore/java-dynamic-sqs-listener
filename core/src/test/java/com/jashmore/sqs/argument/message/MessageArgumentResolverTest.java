package com.jashmore.sqs.argument.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.argument.DefaultMethodParameter;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.argument.payload.Payload;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;

class MessageArgumentResolverTest {

    private final MessageArgumentResolver messageArgumentResolver = new MessageArgumentResolver();

    @Test
    void canResolveMethodArgumentsThatAreTheMessageType() throws Exception {
        // arrange
        final Method method = MessageArgumentResolverTest.class.getMethod("consume", Message.class);
        final MethodParameter methodParameter = DefaultMethodParameter
            .builder()
            .method(method)
            .parameter(method.getParameters()[0])
            .parameterIndex(0)
            .build();

        // act
        final boolean canResolveParameter = messageArgumentResolver.canResolveParameter(methodParameter);

        // assert
        assertThat(canResolveParameter).isTrue();
    }

    @Test
    void canNotResolveMethodArgumentsThatAreNotTheMessageType() throws Exception {
        final Method method = MessageArgumentResolverTest.class.getMethod("consume", String.class);
        final MethodParameter methodParameter = DefaultMethodParameter
            .builder()
            .method(method)
            .parameter(method.getParameters()[0])
            .parameterIndex(0)
            .build();

        // act
        final boolean canResolveParameter = messageArgumentResolver.canResolveParameter(methodParameter);

        // assert
        assertThat(canResolveParameter).isFalse();
    }

    @Test
    void methodResolutionReturnsMessagePassedIn() throws Exception {
        final Method method = MessageArgumentResolverTest.class.getMethod("consume", String.class);
        final MethodParameter methodParameter = DefaultMethodParameter
            .builder()
            .method(method)
            .parameter(method.getParameters()[0])
            .parameterIndex(0)
            .build();
        final Message message = Message.builder().build();

        // act
        final Message resolvedMessage = messageArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(resolvedMessage).isSameAs(message);
    }

    @SuppressWarnings({ "unused", "WeakerAccess" })
    public void consume(final Message message) {}

    @SuppressWarnings({ "unused", "WeakerAccess" })
    public void consume(@Payload final String payload) {}
}
