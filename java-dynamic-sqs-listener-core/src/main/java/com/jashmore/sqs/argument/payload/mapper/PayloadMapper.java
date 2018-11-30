package com.jashmore.sqs.argument.payload.mapper;

import com.amazonaws.services.sqs.model.Message;

/**
 * Mapper that is able to map the message body to an object of a certain type.
 *
 * <p>For example, if the message body is a JSON object this can be used to map this to a Java Bean with the fields built
 * from the JSON body.
 */
public interface PayloadMapper {
    /**
     * Cast the message body to the provided class type.
     *
     * @param message the message to map the body from
     * @param clazz   the class to build the object from the message body
     * @return the message body as an object of the given class type
     * @throws PayloadMappingException exception thrown if there was a failure to map the message body to the defined type
     */
    Object map(final Message message, final Class<?> clazz) throws PayloadMappingException;
}
