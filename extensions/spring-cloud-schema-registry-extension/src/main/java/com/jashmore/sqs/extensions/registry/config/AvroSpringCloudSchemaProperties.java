package com.jashmore.sqs.extensions.registry.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("spring.cloud.schema.avro")
public class AvroSpringCloudSchemaProperties {
    /**
     * The locations of all of the schemas to use for this service.
     */
    private List<Resource> schemaImports;
    /**
     * The locations of all of the schemas to use for this service.
     */
    private List<Resource> schemaLocations;
}
