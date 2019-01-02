package com.jashmore.sqs.argument.payload.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.isA;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.IOException;

public class JacksonPayloadMapperTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private ObjectMapper objectMapper;

    private PayloadMapper payloadMapper;

    @Before
    public void setUp() {
        payloadMapper = new JacksonPayloadMapper(objectMapper);
    }

    @Test
    public void stringPayloadParameterResolvesWithMessageBody() {
        // arrange
        final Message message = Message.builder().body("body").build();

        // act
        final Object argument = payloadMapper.map(message, String.class);

        // assert
        assertThat(argument).isEqualTo("body");
    }

    @Test
    public void payloadContainerPojoCanBeMappedToObject() throws IOException {
        // arrange
        final Message message = Message.builder().build();
        final Pojo parsedObject = new Pojo("test");
        when(objectMapper.readValue(anyString(), eq(Pojo.class))).thenReturn(parsedObject);

        // act
        final Object argument = payloadMapper.map(message, Pojo.class);

        // assert
        assertThat(argument).isEqualTo(parsedObject);
    }

    @Test
    public void errorBuildingPayloadThrowsArgumentResolutionException() throws IOException {
        // arrange
        final Message message = Message.builder().build();
        when(objectMapper.readValue(anyString(), eq(Pojo.class))).thenThrow(new IOException());
        expectedException.expect(PayloadMappingException.class);
        expectedException.expectCause(isA(IOException.class));

        // act
        payloadMapper.map(message, Pojo.class);
    }

    @SuppressWarnings("WeakerAccess")
    public static class Pojo {
        private final String field;

        public Pojo(final String field) {
            this.field = field;
        }

        @SuppressWarnings("unused")
        public String getField() {
            return field;
        }
    }

}
