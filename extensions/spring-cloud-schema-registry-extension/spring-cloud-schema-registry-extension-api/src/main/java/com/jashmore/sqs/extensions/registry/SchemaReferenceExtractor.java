package com.jashmore.sqs.extensions.registry;

import com.jashmore.documentation.annotations.ThreadSafe;
import org.springframework.cloud.schema.registry.SchemaReference;
import software.amazon.awssdk.services.sqs.model.Message;

@ThreadSafe
@FunctionalInterface
public interface SchemaReferenceExtractor {
    /**
     * Obtain the {@link SchemaReference} from the message to use for determining what schema should be used to deserialize the message.
     *
     * <p>This could be obtained by looking at the content type of the {@link Message}, for example the {@link MessageAttributeSchemaReferenceExtractor}
     * which looks at the message attribute of the message to get the version, etc.
     *
     * @param message the message to process
     * @return the schema reference for this message
     */
    SchemaReference extract(Message message);
}
