package com.jashmore.sqs.argument.attribute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.APPROXIMATE_FIRST_RECEIVE_TIMESTAMP;
import static software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.SENDER_ID;
import static software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.SENT_TIMESTAMP;
import static software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.SEQUENCE_NUMBER;

import com.google.common.collect.ImmutableMap;

import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.DefaultMethodParameter;
import com.jashmore.sqs.argument.MethodParameter;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Calendar;

@SuppressWarnings( {"unused", "WeakerAccess"})
public class MessageSystemAttributeArgumentResolverTest {
    private MessageSystemAttributeArgumentResolver messageSystemAttributeArgumentResolver = new MessageSystemAttributeArgumentResolver();

    @Test
    public void systemAttributesThatDoNotExistForNonRequiredAttributeWillReturnNull() throws Exception {
        final Message message = Message.builder()
                .attributes(ImmutableMap.of())
                .build();
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", String.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object value = messageSystemAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isNull();
    }

    @Test
    public void systemAttributesThatDoNotExistForRequiredAttributeWillThrowArgumentResolutionException() throws Exception {
        final Message message = Message.builder()
                .attributes(ImmutableMap.of())
                .build();
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consumeNotRequired", String.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final ArgumentResolutionException exception = assertThrows(ArgumentResolutionException.class,
                () -> messageSystemAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message));

        // assert
        assertThat(exception).hasMessage("Missing system attribute with name: " + SENDER_ID.toString());
    }

    @Test
    public void systemAttributesForStringArgumentsCanBeConsumedWhenPresent() throws Exception {
        final Message message = Message.builder()
                .attributes(ImmutableMap.of(
                        SENDER_ID, "value"
                ))
                .build();
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", String.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object object = messageSystemAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(object).isEqualTo("value");
    }

    @Test
    public void systemAttributesForIntegerArgumentsCanBeConsumed() throws Exception {
        final Message message = Message.builder()
                .attributes(ImmutableMap.of(
                        SEQUENCE_NUMBER, "123"
                ))
                .build();
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", Integer.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object object = messageSystemAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(object).isEqualTo(123);
    }

    @Test
    public void systemAttributesForIntArgumentsCanBeConsumed() throws Exception {
        final Message message = Message.builder()
                .attributes(ImmutableMap.of(
                        APPROXIMATE_FIRST_RECEIVE_TIMESTAMP, "123"
                ))
                .build();
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", int.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object object = messageSystemAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(object).isEqualTo(123);
    }


    @Test
    public void systemAttributesForIntegerArgumentsThatAreNotIntegersThrowsArgumentResolutionException() throws Exception {
        final Message message = Message.builder()
                .attributes(ImmutableMap.of(
                        APPROXIMATE_FIRST_RECEIVE_TIMESTAMP, "invalid"
                ))
                .build();
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", int.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final ArgumentResolutionException exception = assertThrows(ArgumentResolutionException.class,
                () -> messageSystemAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message));

        // assert
        assertThat(exception.getCause()).isInstanceOf(NumberFormatException.class);
    }

    @Test
    public void systemAttributesForLongClassArgumentsCanBeConsumed() throws Exception {
        final Message message = Message.builder()
                .attributes(ImmutableMap.of(
                        SENT_TIMESTAMP, "123"
                ))
                .build();
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", Long.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object object = messageSystemAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(object).isEqualTo(123L);
    }

    @Test
    public void systemAttributesForLongArgumentsCanBeConsumed() throws Exception {
        final Message message = Message.builder()
                .attributes(ImmutableMap.of(
                        SEQUENCE_NUMBER, "123"
                ))
                .build();
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", long.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object object = messageSystemAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(object).isEqualTo(123L);
    }

    @Test
    public void systemAttributesForLongArgumentsThatAreNotLongsThrowsArgumentResolutionException() throws Exception {
        final Message message = Message.builder()
                .attributes(ImmutableMap.of(
                        SEQUENCE_NUMBER, "invalid"
                ))
                .build();
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", long.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final ArgumentResolutionException exception = assertThrows(ArgumentResolutionException.class,
                () -> messageSystemAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message));

        // assert
        assertThat(exception.getCause()).isInstanceOf(NumberFormatException.class);
    }

    @Test
    public void timestampSystemAttributesCanBeCastToOffsetDateTime() throws Exception {
        final OffsetDateTime time = OffsetDateTime.parse("2007-12-03T10:15:30.000Z");
        final Message message = Message.builder()
                .attributes(ImmutableMap.of(
                        SENT_TIMESTAMP, String.valueOf(time.toInstant().toEpochMilli())
                ))
                .build();
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", OffsetDateTime.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object object = messageSystemAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(object).isEqualTo(time);
    }

    @Test
    public void timestampSystemAttributesCanBeCastToInstant() throws Exception {
        final Instant time = OffsetDateTime.parse("2007-12-03T10:15:30.000Z").toInstant();
        final Message message = Message.builder()
                .attributes(ImmutableMap.of(
                        APPROXIMATE_FIRST_RECEIVE_TIMESTAMP, String.valueOf(time.toEpochMilli())
                ))
                .build();
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", Instant.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object object = messageSystemAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(object).isEqualTo(time);
    }

    @Test
    public void systemAttributesCannotBeCastToUnsupportedClasses() throws Exception {
        final Message message = Message.builder()
                .attributes(ImmutableMap.of(SEQUENCE_NUMBER, "ignored"))
                .build();
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", Float.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final ArgumentResolutionException exception = assertThrows(ArgumentResolutionException.class,
                () -> messageSystemAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message));

        // assert
        assertThat(exception).hasMessage("Unsupported parameter type java.lang.Float for system attribute SequenceNumber");
    }

    @Test
    public void timestampSystemAttributesCannotBeCastToUnsupportedClasses() throws Exception {
        final Instant time = OffsetDateTime.parse("2007-12-03T10:15:30.000Z").toInstant();
        final Message message = Message.builder()
                .attributes(ImmutableMap.of(
                        APPROXIMATE_FIRST_RECEIVE_TIMESTAMP, String.valueOf(time.toEpochMilli())
                ))
                .build();
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", Calendar.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final ArgumentResolutionException exception = assertThrows(ArgumentResolutionException.class,
                () -> messageSystemAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message));

        // assert
        assertThat(exception).hasMessage("Unsupported parameter type java.util.Calendar for system attribute ApproximateFirstReceiveTimestamp");
    }

    @Test
    public void canResolveArgumentsWithMessageSystemAttributeAnnotation() throws Exception {
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", OffsetDateTime.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final boolean canResolve = messageSystemAttributeArgumentResolver.canResolveParameter(methodParameter);

        // assert
        assertThat(canResolve).isTrue();
    }

    @Test
    public void cannotResolveArgumentsWithNoMessageSystemAttributeAnnotation() throws Exception {
        final Method method = MessageSystemAttributeArgumentResolverTest.class.getMethod("consume", String.class, String.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final boolean canResolve = messageSystemAttributeArgumentResolver.canResolveParameter(methodParameter);

        // assert
        assertThat(canResolve).isFalse();
    }

    public void consume(@MessageSystemAttribute(SENDER_ID) final String senderId) {
    }

    public void consumeNotRequired(@MessageSystemAttribute(value = SENDER_ID, required = true) final String senderId) {
    }

    public void consume(@MessageSystemAttribute(SEQUENCE_NUMBER) final Integer sequenceNumber) {
    }

    public void consume(@MessageSystemAttribute(APPROXIMATE_FIRST_RECEIVE_TIMESTAMP) final int approximateFirstReceiveTimestamp) {
    }

    public void consume(@MessageSystemAttribute(SENT_TIMESTAMP) final Long sentTimestamp) {
    }

    public void consume(@MessageSystemAttribute(SENT_TIMESTAMP) final OffsetDateTime sentTimestamp) {
    }

    public void consume(@MessageSystemAttribute(APPROXIMATE_FIRST_RECEIVE_TIMESTAMP) final Instant approximateFirstReceiveTimestamp) {
    }

    public void consume(@MessageSystemAttribute(APPROXIMATE_FIRST_RECEIVE_TIMESTAMP) final Calendar approximateFirstReceiveTimestamp) {
    }

    public void consume(@MessageSystemAttribute(SEQUENCE_NUMBER) final Float sequenceNumber) {
    }

    public void consume(@MessageSystemAttribute(SEQUENCE_NUMBER) final long sequenceNumber) {
    }

    public void consume(final String param, final String otherParam) {

    }
}