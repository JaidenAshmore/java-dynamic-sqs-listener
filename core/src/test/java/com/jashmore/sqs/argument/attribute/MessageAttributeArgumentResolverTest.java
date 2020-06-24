package com.jashmore.sqs.argument.attribute;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.DefaultMethodParameter;
import com.jashmore.sqs.argument.MethodParameter;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.lang.reflect.Method;

@Slf4j
@SuppressWarnings("checkstyle:ParameterName")
class MessageAttributeArgumentResolverTest {
    private final MessageAttributeArgumentResolver messageAttributeArgumentResolver = new MessageAttributeArgumentResolver(new ObjectMapper());

    @Test
    void stringMessageAttributesCanBeObtainedFromMessage() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "string", MessageAttributeValue.builder()
                                .dataType(MessageAttributeDataTypes.STRING.getValue())
                                .stringValue("my attribute value")
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consume", String.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object value = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isEqualTo("my attribute value");
    }

    @Test
    void unknownDataTypeWillThrowArgumentResolutionException() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "string", MessageAttributeValue.builder()
                                .dataType("Unknown")
                                .stringValue("my attribute value")
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consume", String.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final ArgumentResolutionException exception = Assertions.assertThrows(ArgumentResolutionException.class,
                () -> messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message));

        // assert
        assertThat(exception).hasMessage("Cannot parse message attribute due to unknown data type 'Unknown'");
    }

    @Test
    void missingMessageAttributeWillReturnNullWhenNotRequired() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(emptyMap())
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consume", String.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object value = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isNull();
    }

    @Test
    void missingMessageAttributeWillThrowArgumentResolutionExceptionWhenRequired() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(emptyMap())
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consumeWithRequiredAttribute", String.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final ArgumentResolutionException exception = Assertions.assertThrows(ArgumentResolutionException.class,
                () -> messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message));

        // assert
        assertThat(exception).hasMessage("Required Message Attribute 'string' is missing from message");
    }

    @Test
    void floatCanBeCreatedFromNumberMessageAttributes() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "float", MessageAttributeValue.builder()
                                .dataType("Number.float")
                                .stringValue("1.0")
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consumeNumbers", float.class, int.class,
                long.class, byte.class, short.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Float value = (Float) messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isCloseTo(1.0f, Percentage.withPercentage(0.1));
    }

    @Test
    void integerCanBeCreatedFromNumberMessageAttributes() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "int", MessageAttributeValue.builder()
                                .dataType("Number.int")
                                .stringValue("1.0")
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consumeNumbers", float.class, int.class,
                long.class, byte.class, short.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[1])
                .parameterIndex(1)
                .build();

        // act
        final Object value = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isEqualTo(1);
    }

    @Test
    void longCanBeCreatedFromNumberMessageAttributes() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "long", MessageAttributeValue.builder()
                                .dataType("Number.long")
                                .stringValue("1234")
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consumeNumbers", float.class, int.class,
                long.class, byte.class, short.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[2])
                .parameterIndex(2)
                .build();

        // act
        final Object value = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isEqualTo(1234L);
    }

    @Test
    void byteCanBeCreatedFromNumberMessageAttributes() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "byte", MessageAttributeValue.builder()
                                .dataType("Number.byte")
                                .stringValue("1")
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consumeNumbers", float.class, int.class,
                long.class, byte.class, short.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[3])
                .parameterIndex(3)
                .build();

        // act
        final Object value = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isEqualTo(new Byte("1"));
    }


    @Test
    void shortCanBeCreatedFromNumberMessageAttributes() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "short", MessageAttributeValue.builder()
                                .dataType("Number.short")
                                .stringValue("1")
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consumeNumbers", float.class, int.class,
                long.class, byte.class, short.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[4])
                .parameterIndex(4)
                .build();

        // act
        final Object value = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isEqualTo((short) 1);
    }

    @Test
    void floatClassCanBeCreatedFromNumberMessageAttributes() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "float", MessageAttributeValue.builder()
                                .dataType("Number.float")
                                .stringValue("1.0")
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consumeNumbers", Float.class, Integer.class,
                Long.class, Byte.class, Short.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Float value = (Float) messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isCloseTo(1.0f, Percentage.withPercentage(0.1));
    }

    @Test
    void integerClassCanBeCreatedFromNumberMessageAttributes() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "int", MessageAttributeValue.builder()
                                .dataType("Number.int")
                                .stringValue("1.0")
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consumeNumbers", Float.class, Integer.class,
                Long.class, Byte.class, Short.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[1])
                .parameterIndex(1)
                .build();

        // act
        final Object value = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isEqualTo(1);
    }

    @Test
    void longClassCanBeCreatedFromNumberMessageAttributes() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "long", MessageAttributeValue.builder()
                                .dataType("Number.long")
                                .stringValue("1234")
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consumeNumbers", Float.class, Integer.class,
                Long.class, Byte.class, Short.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[2])
                .parameterIndex(2)
                .build();

        // act
        final Object value = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isEqualTo(1234L);
    }

    @Test
    void byteClassCanBeCreatedFromNumberMessageAttributes() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "byte", MessageAttributeValue.builder()
                                .dataType("Number.byte")
                                .stringValue("12")
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consumeNumbers", Float.class, Integer.class,
                Long.class, Byte.class, Short.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[3])
                .parameterIndex(3)
                .build();

        // act
        final Object value = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isEqualTo(new Byte("12"));
    }

    @Test
    void shortClassCanBeCreatedFromNumberMessageAttributes() throws Exception {
        // arrange
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "short", MessageAttributeValue.builder()
                                .dataType("Number.short")
                                .stringValue("12")
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consumeNumbers", Float.class, Integer.class,
                Long.class, Byte.class, Short.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[4])
                .parameterIndex(4)
                .build();

        // act
        final Object value = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isEqualTo(new Short("12"));
    }

    @Test
    void pojoCanBeDeserialisedFromMessageAttribute() throws Exception {
        final MyPojo pojo = MyPojo.builder().name("name").build();
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "pojo", MessageAttributeValue.builder()
                                .dataType(MessageAttributeDataTypes.STRING.getValue())
                                .stringValue(new ObjectMapper().writeValueAsString(pojo))
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consume", MyPojo.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object value = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(value).isEqualTo(pojo);
    }

    @Test
    void attributeThatCannotBeProperlyParsedThrowsArgumentResolutionException() throws Exception {
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "pojo", MessageAttributeValue.builder()
                                .dataType(MessageAttributeDataTypes.STRING.getValue())
                                .stringValue("Expected Test Exception")
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consume", MyPojo.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final ArgumentResolutionException exception = Assertions.assertThrows(ArgumentResolutionException.class,
                () -> messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message));

        // assert
        assertThat(exception).hasMessage("Error parsing Message Attribute 'pojo'");
    }

    @Test
    void canExtractBytesFromBinaryMessageAttribute() throws Exception {
        final byte[] binaryBytes = "some string".getBytes();
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "bytes", MessageAttributeValue.builder()
                                .dataType(MessageAttributeDataTypes.BINARY.getValue())
                                .binaryValue(SdkBytes.fromByteArray(binaryBytes))
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consume", byte[].class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object object = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(object).isEqualTo(binaryBytes);
    }

    @Test
    void canParseStringFromBinaryMessageAttribute() throws Exception {
        final String expectedString = "some string";
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "string", MessageAttributeValue.builder()
                                .dataType(MessageAttributeDataTypes.BINARY.getValue())
                                .binaryValue(SdkBytes.fromByteArray(expectedString.getBytes()))
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consume", String.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object object = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(object).isEqualTo(expectedString);
    }

    @Test
    void canParseObjectFromBinaryMessageAttribute() throws Exception {
        final MyPojo myPojo = MyPojo.builder().name("name").build();
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "pojo", MessageAttributeValue.builder()
                                .dataType(MessageAttributeDataTypes.BINARY.getValue())
                                .binaryValue(SdkBytes.fromByteArray(new ObjectMapper().writeValueAsBytes(myPojo)))
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consume", MyPojo.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final Object object = messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message);

        // assert
        assertThat(object).isEqualTo(myPojo);
    }

    @Test
    void failureParseObjectFromBinaryMessageAttributeWillThrowArgumentResolutionException() throws Exception {
        // arrange
        final Message message = Message.builder()
                .messageAttributes(singletonMap(
                        "pojo", MessageAttributeValue.builder()
                                .dataType(MessageAttributeDataTypes.BINARY.getValue())
                                .binaryValue(SdkBytes.fromByteArray("My String".getBytes()))
                                .build()
                ))
                .build();
        final Method method = MessageAttributeArgumentResolverTest.class.getMethod("consume", MyPojo.class);
        final MethodParameter methodParameter = DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build();

        // act
        final ArgumentResolutionException exception = Assertions.assertThrows(ArgumentResolutionException.class,
                () -> messageAttributeArgumentResolver.resolveArgumentForParameter(null, methodParameter, message));

        // assert
        assertThat(exception).hasMessage("Failure to parse binary bytes to '" + MyPojo.class.getName() + "'");
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void consumeWithRequiredAttribute(@MessageAttribute(value = "string", required = true) final String messageAttribute) {
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void consumeNumbers(@MessageAttribute("float") float f,
                               @MessageAttribute("int") int i,
                               @MessageAttribute("long") long l,
                               @MessageAttribute("byte") byte b,
                               @MessageAttribute("short") short s) {
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void consumeNumbers(@MessageAttribute("float") Float f,
                               @MessageAttribute("int") Integer i,
                               @MessageAttribute("long") Long l,
                               @MessageAttribute("byte") Byte b,
                               @MessageAttribute("short") Short s) {
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void consume(@MessageAttribute("string") final String messageAttribute) {
    }


    @SuppressWarnings({"unused", "WeakerAccess"})
    public void consume(@MessageAttribute("pojo") final MyPojo pojo) {

    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void consume(@MessageAttribute("bytes") final byte[] b) {

    }

    @Value
    @Builder
    @SuppressWarnings("WeakerAccess")
    public static class MyPojo {
        String name;
    }
}
