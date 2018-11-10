package com.jashmore.sqs.argument.payload.mapper;

import com.amazonaws.services.sqs.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;

import java.io.IOException;

/**
 * Cast the message body to a Java Bean using a Jackson {@link ObjectMapper}.
 */
@AllArgsConstructor
public class JacksonPayloadMapper implements PayloadMapper {
    private final ObjectMapper objectMapper;

    @Override
    public Object cast(Message message, Class<?> clazz) throws PayloadMappingException {
        if (clazz.equals(String.class)) {
            return message.getBody();
        }

        try {
            return objectMapper.readValue(message.getBody(), clazz);
        } catch (final IOException exception) {
            throw new PayloadMappingException("Error trying to resolve Payload for argument", exception);
        }
    }
}
