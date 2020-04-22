package com.jashmore.sqs.extensions.registry.avro;

import com.jashmore.sqs.extensions.registry.SpringCloudSchemaArgumentResolver;
import com.jashmore.sqs.extensions.registry.config.AvroSpringCloudSchemaProperties;
import com.jashmore.sqs.extensions.registry.config.SpringCloudSchemaSqsConfiguration;
import com.jashmore.sqs.extensions.registry.consumer.ConsumerSchemaProvider;
import com.jashmore.sqs.extensions.registry.deserializer.MessagePayloadDeserializer;
import com.jashmore.sqs.extensions.registry.producer.InMemoryCachingProducerSchemaProvider;
import com.jashmore.sqs.extensions.registry.producer.ProducerSchemaProvider;
import com.jashmore.sqs.extensions.registry.schemareference.SchemaReferenceExtractor;
import org.apache.avro.Schema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.schema.registry.avro.AvroSchemaServiceManager;
import org.springframework.cloud.schema.registry.client.SchemaRegistryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnClass(name = "org.apache.avro.Schema")
@ConditionalOnBean(type = "org.springframework.cloud.schema.registry.client.SchemaRegistryClient")
@ConditionalOnProperty(value = "spring.cloud.schema.avro.sqs.enabled", matchIfMissing = true)
@Import(SpringCloudSchemaSqsConfiguration.class)
@SuppressWarnings("checkstyle:javadocmethod")
public class AvroSqsSpringCloudSchemaRegistryConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConsumerSchemaProvider<Schema> consumerSchemaProvider(final AvroSpringCloudSchemaProperties avroSpringCloudSchemaProperties) {
        return new AvroClasspathConsumerSchemaProvider(
                avroSpringCloudSchemaProperties.getSchemaImports(),
                avroSpringCloudSchemaProperties.getSchemaLocations()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ProducerSchemaProvider<Schema> producerSchemaProvider(final SchemaRegistryClient schemaRegistryClient) {
        return new InMemoryCachingProducerSchemaProvider<>(
                new AvroSchemaRegistryProducerSchemaProvider(schemaRegistryClient)
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public MessagePayloadDeserializer<Schema> schemaMessageTransformer(final AvroSchemaServiceManager avroSchemaServiceManager) {
        return new AvroMessagePayloadDeserializer(avroSchemaServiceManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringCloudSchemaArgumentResolver<Schema> springCloudSchemaArgumentResolver(final SchemaReferenceExtractor schemaReferenceExtractor,
                                                                               final ConsumerSchemaProvider<Schema> consumerSchemaProvider,
                                                                               final ProducerSchemaProvider<Schema> producerSchemaProvider,
                                                                               final MessagePayloadDeserializer<Schema> messagePayloadDeserializer) {
        return new SpringCloudSchemaArgumentResolver<>(schemaReferenceExtractor, consumerSchemaProvider, producerSchemaProvider, messagePayloadDeserializer);
    }
}
