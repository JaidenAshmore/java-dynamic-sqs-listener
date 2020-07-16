package com.jashmore.sqs.decorator;

import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.sqs.QueueProperties;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class MessageProcessingContext {
    /**
     * The unique identifier for this message listener.
     *
     * <p>For example: my-message-consumer or any other custom identifier.
     */
    @NonNull
    String listenerIdentifier;

    /**
     * The details about the queue that this message processor is running against.
     */
    @NonNull
    QueueProperties queueProperties;

    /**
     * Processing attributes that you can use to share objects through each stage of the processing.
     *
     * <p>For example, you may want to store the start time for when the message has begun to be processing
     * for usage by a later decorator method to determine the time to process the message.
     */
    @NonNull
    Map<String, Object> attributes;

    /**
     * Helper method to get an attribute with the given key.
     *
     * @param key the key of the attribute
     * @return the value of the attribute or null if it does not exist
     * @param <T> the type of the attribute to cast the value to
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(final String key) {
        return (T) attributes.get(key);
    }

    /**
     * Helper method to set an attribute with the given key.
     *MessageProcessingDecorat
     * @param key   the key of the attribute
     * @param value the value to set for the attribute
     * @return this object for further chaining if necessary
     */
    public MessageProcessingContext setAttribute(final String key, @Nullable final Object value) {
        attributes.put(key, value);
        return this;
    }
}
