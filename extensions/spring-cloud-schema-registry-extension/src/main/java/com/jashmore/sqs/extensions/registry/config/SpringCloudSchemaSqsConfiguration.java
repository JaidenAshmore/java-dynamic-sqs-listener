package com.jashmore.sqs.extensions.registry.config;

import com.jashmore.sqs.extensions.registry.schemareference.MessageAttributeSchemaReferenceExtractor;
import com.jashmore.sqs.extensions.registry.schemareference.SchemaReferenceExtractor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.schema.registry.client.EnableSchemaRegistryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableSchemaRegistryClient
@EnableConfigurationProperties(AvroSpringCloudSchemaProperties.class)
@SuppressWarnings("checkstyle:javadocmethod")
public class SpringCloudSchemaSqsConfiguration {
    @Bean
    @ConditionalOnMissingBean(SchemaReferenceExtractor.class)
    public SchemaReferenceExtractor schemaReferenceExtractor() {
        return new MessageAttributeSchemaReferenceExtractor();
    }
}
