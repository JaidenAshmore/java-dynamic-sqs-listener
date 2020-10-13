package com.jashmore.sqs.extensions.registry.avro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.extensions.registry.MessagePayloadDeserializerException;
import java.io.IOException;
import java.util.Base64;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.schema.registry.avro.AvroSchemaServiceManager;
import software.amazon.awssdk.services.sqs.model.Message;

@ExtendWith(MockitoExtension.class)
class AvroMessagePayloadDeserializerTest {

    @Mock
    private AvroSchemaServiceManager avroSchemaServiceManager;

    @Test
    void canDeserializePayloadOfMessage() throws IOException {
        // arrange
        final byte[] payloadAsBytes = "some message".getBytes();
        final Message message = Message.builder().body(Base64.getEncoder().encodeToString(payloadAsBytes)).build();
        when(avroSchemaServiceManager.readData(eq(String.class), eq(payloadAsBytes), any(Schema.class), any(Schema.class)))
            .thenReturn("Result");

        // act
        final Object deserializedObject = new AvroMessagePayloadDeserializer(avroSchemaServiceManager)
        .deserialize(message, mock(Schema.class), mock(Schema.class), String.class);

        // assert
        assertThat(deserializedObject).isEqualTo("Result");
    }

    @Test
    void appliesDeserializingFunctionToMessageContent() throws IOException {
        // arrange
        final byte[] transformedBytes = { 1, 2 };
        final Message message = Message.builder().body("payload").build();
        when(avroSchemaServiceManager.readData(eq(String.class), eq(transformedBytes), any(Schema.class), any(Schema.class)))
            .thenReturn("Result");

        // act
        final Object deserializedObject = new AvroMessagePayloadDeserializer(avroSchemaServiceManager, m -> transformedBytes)
        .deserialize(message, mock(Schema.class), mock(Schema.class), String.class);

        // assert
        assertThat(deserializedObject).isEqualTo("Result");
    }

    @Test
    void exceptionDeserializingPayloadThrowsException() throws IOException {
        // arrange
        final byte[] payloadAsBytes = "some message".getBytes();
        final Message message = Message.builder().body(Base64.getEncoder().encodeToString(payloadAsBytes)).build();
        when(avroSchemaServiceManager.readData(eq(String.class), any(), any(Schema.class), any(Schema.class))).thenThrow(IOException.class);
        final AvroMessagePayloadDeserializer avroMessagePayloadDeserializer = new AvroMessagePayloadDeserializer(avroSchemaServiceManager);

        // act
        assertThrows(
            MessagePayloadDeserializerException.class,
            () -> avroMessagePayloadDeserializer.deserialize(message, mock(Schema.class), mock(Schema.class), String.class)
        );
    }
}
