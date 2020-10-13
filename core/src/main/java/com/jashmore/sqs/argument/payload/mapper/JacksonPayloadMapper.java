package com.jashmore.sqs.argument.payload.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Cast the message body to a Java Bean using a Jackson {@link ObjectMapper}.
 */
@AllArgsConstructor
public class JacksonPayloadMapper implements PayloadMapper {

    private final ObjectMapper objectMapper;

    @Override
    public Object map(Message message, Class<?> clazz) throws PayloadMappingException {
        if (clazz.equals(String.class)) {
            return message.body();
        }

        try {
            return objectMapper.readValue(message.body(), clazz);
        } catch (final IOException exception) {
            throw new PayloadMappingException("Error trying to resolve Payload for argument", exception);
        }
    }
}
