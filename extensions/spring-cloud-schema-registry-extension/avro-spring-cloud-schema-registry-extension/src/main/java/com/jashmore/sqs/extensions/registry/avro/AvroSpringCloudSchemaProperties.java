package com.jashmore.sqs.extensions.registry.avro;

import com.jashmore.documentation.annotations.Nullable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("spring.cloud.schema.avro")
public class AvroSpringCloudSchemaProperties {

    /**
     * The list of schema resources that should be loaded before the {@link #schemaLocations} for the
     * scenario that these are reliant on other schemas.
     */
    @Nullable
    private List<Resource> schemaImports;

    /**
     * The locations of the schemas to use for this service.
     *
     * <p>These schemas can be dependent on other schema's defined in the {@link #schemaImports} property.
     */
    @Nullable
    private List<Resource> schemaLocations;
}
