package com.jashmore.sqs.extensions.registry.avro;

import com.jashmore.sqs.extensions.registry.deserializer.MessagePayloadDeserializer;
import com.jashmore.sqs.extensions.registry.deserializer.SchemaMessageDeserializationException;
import org.apache.avro.Schema;
import org.springframework.cloud.schema.registry.avro.AvroSchemaServiceManager;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.IOException;
import java.util.Base64;

public class AvroMessagePayloadDeserializer implements MessagePayloadDeserializer<Schema> {
    private final AvroSchemaServiceManager avroSchemaServiceManager;

    public AvroMessagePayloadDeserializer(final AvroSchemaServiceManager avroSchemaServiceManager) {
        this.avroSchemaServiceManager = avroSchemaServiceManager;
    }

    @Override
    public Object deserialize(final Message message, final Schema producerSchema, final Schema consumerSchema, final Class<?> clazz) {
        try {
            return avroSchemaServiceManager.readData(clazz, Base64.getDecoder().decode(message.body()), consumerSchema, producerSchema);
        } catch (IOException ioException) {
            throw new SchemaMessageDeserializationException(ioException);
        }
    }
}
