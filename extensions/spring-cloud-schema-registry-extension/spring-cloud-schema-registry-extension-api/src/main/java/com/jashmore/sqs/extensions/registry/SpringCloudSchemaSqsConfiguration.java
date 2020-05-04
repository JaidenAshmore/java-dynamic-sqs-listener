package com.jashmore.sqs.extensions.registry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.schema.registry.client.EnableSchemaRegistryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableSchemaRegistryClient
@SuppressWarnings("checkstyle:javadocmethod")
public class SpringCloudSchemaSqsConfiguration {
    @Bean
    @ConditionalOnMissingBean(SchemaReferenceExtractor.class)
    public SchemaReferenceExtractor schemaReferenceExtractor() {
        return new MessageAttributeSchemaReferenceExtractor();
    }
}
