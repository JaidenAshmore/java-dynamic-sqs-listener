package com.jashmore.sqs.argument.payload.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.Message;

@ExtendWith(MockitoExtension.class)
class JacksonPayloadMapperTest {
    @Mock
    private ObjectMapper objectMapper;

    private PayloadMapper payloadMapper;

    @BeforeEach
    void setUp() {
        payloadMapper = new JacksonPayloadMapper(objectMapper);
    }

    @Test
    void stringPayloadParameterResolvesWithMessageBody() {
        // arrange
        final Message message = Message.builder().body("body").build();

        // act
        final Object argument = payloadMapper.map(message, String.class);

        // assert
        assertThat(argument).isEqualTo("body");
    }

    @Test
    void payloadContainerPojoCanBeMappedToObject() throws IOException {
        // arrange
        final Message message = Message.builder().body("body").build();
        final Pojo parsedObject = new Pojo("test");
        when(objectMapper.readValue(anyString(), eq(Pojo.class))).thenReturn(parsedObject);

        // act
        final Object argument = payloadMapper.map(message, Pojo.class);

        // assert
        assertThat(argument).isEqualTo(parsedObject);
    }

    @Test
    void errorBuildingPayloadThrowsArgumentResolutionException() throws IOException {
        // arrange
        final Message message = Message.builder().body("test").build();
        when(objectMapper.readValue(anyString(), eq(Pojo.class))).thenThrow(JsonProcessingException.class);

        // act
        final PayloadMappingException exception = assertThrows(PayloadMappingException.class, () -> payloadMapper.map(message, Pojo.class));

        // assert
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @SuppressWarnings("WeakerAccess")
    public static class Pojo {
        private final String field;

        public Pojo(final String field) {
            this.field = field;
        }

        @SuppressWarnings("unused")
        String getField() {
            return field;
        }
    }
}
