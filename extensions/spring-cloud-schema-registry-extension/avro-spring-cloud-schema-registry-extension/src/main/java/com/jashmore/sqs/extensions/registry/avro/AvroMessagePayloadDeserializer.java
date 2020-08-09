package com.jashmore.sqs.extensions.registry.avro;

import com.jashmore.sqs.extensions.registry.MessagePayloadDeserializer;
import com.jashmore.sqs.extensions.registry.MessagePayloadDeserializerException;
import java.io.IOException;
import java.util.Base64;
import java.util.function.Function;
import org.apache.avro.Schema;
import org.springframework.cloud.schema.registry.avro.AvroSchemaServiceManager;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Deserializes the message into the pojo defined by the consumer Avro schema by transforming it from the serialized representation by
 * the producer schema.
 */
public class AvroMessagePayloadDeserializer implements MessagePayloadDeserializer<Schema> {
    private final AvroSchemaServiceManager avroSchemaServiceManager;
    private final Function<Message, byte[]> payloadExtractor;

    public AvroMessagePayloadDeserializer(final AvroSchemaServiceManager avroSchemaServiceManager) {
        this(avroSchemaServiceManager, message -> Base64.getDecoder().decode(message.body()));
    }

    public AvroMessagePayloadDeserializer(
        final AvroSchemaServiceManager avroSchemaServiceManager,
        final Function<Message, byte[]> payloadExtractor
    ) {
        this.avroSchemaServiceManager = avroSchemaServiceManager;
        this.payloadExtractor = payloadExtractor;
    }

    @Override
    public Object deserialize(final Message message, final Schema producerSchema, final Schema consumerSchema, final Class<?> clazz) {
        try {
            return avroSchemaServiceManager.readData(clazz, payloadExtractor.apply(message), consumerSchema, producerSchema);
        } catch (IOException ioException) {
            throw new MessagePayloadDeserializerException(ioException);
        }
    }
}
