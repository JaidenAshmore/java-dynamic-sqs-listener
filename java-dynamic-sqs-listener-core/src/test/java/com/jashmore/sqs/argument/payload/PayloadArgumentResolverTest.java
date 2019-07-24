package com.jashmore.sqs.argument.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.DefaultMethodParameter;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMappingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Method;

@ExtendWith(MockitoExtension.class)
class PayloadArgumentResolverTest {
    @Mock
    private PayloadMapper payloadMapper;

    @Mock
    private QueueProperties queueProperties;

    private PayloadArgumentResolver payloadArgumentResolver;

    @BeforeEach
    void setUp() {
        payloadArgumentResolver = new PayloadArgumentResolver(payloadMapper);
    }

    @Test
    void parameterWithNoPayloadAnnotationCannotBeHandled() {
        // arrange
        final MethodParameter numberParameter = getParameter(2);

        // act
        final boolean canHandleParameter = payloadArgumentResolver.canResolveParameter(numberParameter);

        // assert
        assertThat(canHandleParameter).isFalse();
    }

    @Test
    void parameterWithPayloadAnnotationCanBeHandled() {
        // arrange
        final MethodParameter stringParameter = getParameter(0);

        // act
        final boolean canHandleParameter = payloadArgumentResolver.canResolveParameter(stringParameter);

        // assert
        assertThat(canHandleParameter).isTrue();
    }

    @Test
    void payloadThatFailsToBeBuiltThrowsArgumentResolutionException() {
        // arrange
        final MethodParameter stringParameter = getParameter(1);
        final Message message = Message.builder().build();
        when(payloadMapper.map(message, Pojo.class)).thenThrow(new PayloadMappingException("Error"));

        // act
        final ArgumentResolutionException exception = assertThrows(ArgumentResolutionException.class,
                () -> payloadArgumentResolver.resolveArgumentForParameter(queueProperties, stringParameter, message));

        // assert
        assertThat(exception.getCause()).isInstanceOf(PayloadMappingException.class);
    }

    @Test
    void payloadThatIsSuccessfullyBuiltIsReturnedInResolution() {
        final MethodParameter parameter = getParameter(1);
        final Message message = Message.builder().build();
        final Pojo parsedObject = new Pojo("test");
        when(payloadMapper.map(message, Pojo.class)).thenReturn(parsedObject);

        // act
        final Object argument = payloadArgumentResolver.resolveArgumentForParameter(queueProperties, parameter, message);

        // assert
        assertThat(argument).isEqualTo(parsedObject);
    }

    @SuppressWarnings( {"unused"})
    public void method(@Payload final String payloadString, @Payload final Pojo payloadPojo, final String parameterWithNoPayloadAnnotation) {

    }

    private MethodParameter getParameter(final int index) {
        try {
            final Method method = PayloadArgumentResolverTest.class.getMethod("method", String.class, Pojo.class, String.class);
            return DefaultMethodParameter.builder()
                    .method(method)
                    .parameter(method.getParameters()[index])
                    .parameterIndex(index)
                    .build();
        } catch (final NoSuchMethodException noSuchMethodException) {
            throw new RuntimeException(noSuchMethodException);
        }
    }

    @SuppressWarnings( {"WeakerAccess", "unused"})
    public static class Pojo {
        private final String field;

        public Pojo(final String field) {
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }
}
