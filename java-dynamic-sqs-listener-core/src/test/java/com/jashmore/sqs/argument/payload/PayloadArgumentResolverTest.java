package com.jashmore.sqs.argument.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.isA;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMappingException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class PayloadArgumentResolverTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private PayloadMapper payloadMapper;

    @Mock
    private QueueProperties queueProperties;

    private PayloadArgumentResolver payloadArgumentResolver;

    @Before
    public void setUp() {
        payloadArgumentResolver = new PayloadArgumentResolver(payloadMapper);
    }

    @Test
    public void parameterWithNoPayloadAnnotationCannotBeHandled() {
        // arrange
        final Parameter numberParameter = getParameter(2);

        // act
        final boolean canHandleParameter = payloadArgumentResolver.canResolveParameter(numberParameter);

        // assert
        assertThat(canHandleParameter).isFalse();
    }

    @Test
    public void parameterWithPayloadAnnotationCanBeHandled() {
        // arrange
        final Parameter stringParameter = getParameter(0);

        // act
        final boolean canHandleParameter = payloadArgumentResolver.canResolveParameter(stringParameter);

        // assert
        assertThat(canHandleParameter).isTrue();
    }

    @Test
    public void payloadThatFailsToBeBuiltThrowsArgumentResolutionException() {
        // arrange
        final Parameter stringParameter = getParameter(1);
        final Message message = Message.builder().build();
        when(payloadMapper.map(message, Pojo.class)).thenThrow(new PayloadMappingException("Error"));
        expectedException.expect(ArgumentResolutionException.class);
        expectedException.expectCause(isA(PayloadMappingException.class));

        // act
        payloadArgumentResolver.resolveArgumentForParameter(queueProperties, stringParameter, message);
    }

    @Test
    public void payloadThatIsSuccessfullyBuiltIsReturnedInResolution() {
        final Parameter parameter = getParameter(1);
        final Message message = Message.builder().build();
        final Pojo parsedObject = new Pojo("test");
        when(payloadMapper.map(message, Pojo.class)).thenReturn(parsedObject);

        // act
        final Object argument = payloadArgumentResolver.resolveArgumentForParameter(queueProperties, parameter, message);

        // assert
        assertThat(argument).isEqualTo(parsedObject);
    }

    @SuppressWarnings( {"WeakerAccess", "unused"})
    public void method(@Payload final String payloadString, @Payload final Pojo payloadPojo, final String parameterWithNoPayloadAnnotation) {

    }

    private Parameter getParameter(final int index) {
        try {
            final Method method = PayloadArgumentResolverTest.class.getMethod("method", String.class, Pojo.class, String.class);
            return method.getParameters()[index];
        } catch (final NoSuchMethodException noSuchMethodException) {
            throw new RuntimeException(noSuchMethodException);
        }
    }

    @SuppressWarnings("WeakerAccess")
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
