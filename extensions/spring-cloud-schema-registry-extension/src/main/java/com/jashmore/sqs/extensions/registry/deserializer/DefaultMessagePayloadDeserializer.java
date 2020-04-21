package com.jashmore.sqs.extensions.registry.deserializer;

import org.apache.avro.Schema;
import org.springframework.cloud.schema.registry.avro.AvroSchemaServiceManager;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.IOException;
import java.util.Base64;

public class DefaultMessagePayloadDeserializer implements MessagePayloadDeserializer {
    private final AvroSchemaServiceManager avroSchemaServiceManager;

    public DefaultMessagePayloadDeserializer(final AvroSchemaServiceManager avroSchemaServiceManager) {
        this.avroSchemaServiceManager = avroSchemaServiceManager;
    }

    @Override
    public Object deserialize(final Message message, final Schema producerSchema, final Schema consumerSchema, final Class<?> clazz) {
        try {
            return avroSchemaServiceManager.readData(clazz, Base64.getDecoder().decode(message.body()), consumerSchema, producerSchema);
        } catch (IOException e) {
            throw new SchemaMessageDeserializationException(e);
        }
    }
}
