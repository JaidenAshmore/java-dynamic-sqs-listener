package com.jashmore.sqs.extensions.registry.config;

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
@ConfigurationProperties("spring.cloud.schema.sqs")
public class SpringCloudSchemaProperties {
    private AvroProperties avro;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvroProperties {
        /**
         * The locations of all of the schemas to use for this service.
         */
        private Resource[] schemaLocations;
    }
}
