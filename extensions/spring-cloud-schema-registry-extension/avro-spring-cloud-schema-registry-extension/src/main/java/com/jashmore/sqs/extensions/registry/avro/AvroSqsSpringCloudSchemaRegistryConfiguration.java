package com.jashmore.sqs.extensions.registry.avro;

import com.jashmore.sqs.extensions.registry.ConsumerSchemaRetriever;
import com.jashmore.sqs.extensions.registry.InMemoryCachingProducerSchemaRetriever;
import com.jashmore.sqs.extensions.registry.MessagePayloadDeserializer;
import com.jashmore.sqs.extensions.registry.ProducerSchemaRetriever;
import com.jashmore.sqs.extensions.registry.SchemaReferenceExtractor;
import com.jashmore.sqs.extensions.registry.SpringCloudSchemaArgumentResolver;
import com.jashmore.sqs.extensions.registry.SpringCloudSchemaRegistryPayload;
import com.jashmore.sqs.extensions.registry.SpringCloudSchemaSqsConfiguration;
import org.apache.avro.Schema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.schema.registry.avro.AvroSchemaServiceManager;
import org.springframework.cloud.schema.registry.client.SchemaRegistryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(value = "spring.cloud.schema.avro.sqs.enabled", matchIfMissing = true)
@EnableConfigurationProperties(AvroSpringCloudSchemaProperties.class)
@Import(SpringCloudSchemaSqsConfiguration.class)
@SuppressWarnings("checkstyle:javadocmethod")
public class AvroSqsSpringCloudSchemaRegistryConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConsumerSchemaRetriever<Schema> consumerSchemaProvider(final AvroSpringCloudSchemaProperties avroSpringCloudSchemaProperties) {
        return new AvroClasspathConsumerSchemaRetriever(
            avroSpringCloudSchemaProperties.getSchemaImports(),
            avroSpringCloudSchemaProperties.getSchemaLocations()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ProducerSchemaRetriever<Schema> producerSchemaProvider(final SchemaRegistryClient schemaRegistryClient) {
        return new InMemoryCachingProducerSchemaRetriever<>(new AvroSchemaRegistryProducerSchemaRetriever(schemaRegistryClient));
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public MessagePayloadDeserializer<Schema> schemaMessageTransformer(final AvroSchemaServiceManager avroSchemaServiceManager) {
        return new AvroMessagePayloadDeserializer(avroSchemaServiceManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringCloudSchemaArgumentResolver<Schema> springCloudSchemaArgumentResolver(
        final SchemaReferenceExtractor schemaReferenceExtractor,
        final ConsumerSchemaRetriever<Schema> consumerSchemaRetriever,
        final ProducerSchemaRetriever<Schema> producerSchemaProvider,
        final MessagePayloadDeserializer<Schema> messagePayloadDeserializer
    ) {
        return new SpringCloudSchemaArgumentResolver<>(
            schemaReferenceExtractor,
            consumerSchemaRetriever,
            producerSchemaProvider,
            messagePayloadDeserializer,
            SpringCloudSchemaRegistryPayload.class
        );
    }
}
