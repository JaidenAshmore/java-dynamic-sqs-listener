package com.jashmore.sqs.extensions.registry.config;

import com.jashmore.sqs.extensions.registry.producer.InMemoryCachingProducerSchemaProvider;
import com.jashmore.sqs.extensions.registry.schemareference.MessageAttributeSchemaReferenceExtractor;
import com.jashmore.sqs.extensions.registry.schemareference.SchemaReferenceExtractor;
import com.jashmore.sqs.extensions.registry.SpringCloudSchemaArgumentResolver;
import com.jashmore.sqs.extensions.registry.consumer.ClasspathConsumerSchemaProvider;
import com.jashmore.sqs.extensions.registry.consumer.ConsumerSchemaProvider;
import com.jashmore.sqs.extensions.registry.deserializer.DefaultMessagePayloadDeserializer;
import com.jashmore.sqs.extensions.registry.deserializer.MessagePayloadDeserializer;
import com.jashmore.sqs.extensions.registry.producer.ProducerSchemaProvider;
import com.jashmore.sqs.extensions.registry.producer.SchemaRegistryProducerSchemaProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.schema.registry.avro.AvroSchemaServiceManager;
import org.springframework.cloud.schema.registry.client.EnableSchemaRegistryClient;
import org.springframework.cloud.schema.registry.client.SchemaRegistryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.Optional;

@Configuration
@EnableSchemaRegistryClient
@EnableConfigurationProperties(SpringCloudSchemaProperties.class)
public class SpringCloudSchemaSqsConfiguration {

    @Bean
    @ConditionalOnMissingBean(SchemaReferenceExtractor.class)
    public SchemaReferenceExtractor schemaReferenceExtractor() {
        return new MessageAttributeSchemaReferenceExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConsumerSchemaProvider consumerSchemaProvider(final SpringCloudSchemaProperties springCloudSchemaProperties) {
        final Resource[] resourceLocations = Optional.ofNullable(springCloudSchemaProperties.getAvro())
                .map(SpringCloudSchemaProperties.AvroProperties::getSchemaLocations)
                .orElse(new Resource[]{});
        return new ClasspathConsumerSchemaProvider(resourceLocations);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProducerSchemaProvider producerSchemaProvider(final SchemaRegistryClient schemaRegistryClient) {
        return new InMemoryCachingProducerSchemaProvider(
                new SchemaRegistryProducerSchemaProvider(schemaRegistryClient)
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public MessagePayloadDeserializer schemaMessageTransformer(final AvroSchemaServiceManager avroSchemaServiceManager) {
        return new DefaultMessagePayloadDeserializer(avroSchemaServiceManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringCloudSchemaArgumentResolver springCloudSchemaArgumentResolver(final SchemaReferenceExtractor schemaReferenceExtractor,
                                                                               final ConsumerSchemaProvider consumerSchemaProvider,
                                                                               final ProducerSchemaProvider producerSchemaProvider,
                                                                               final MessagePayloadDeserializer messagePayloadDeserializer) {
        return new SpringCloudSchemaArgumentResolver(schemaReferenceExtractor, consumerSchemaProvider, producerSchemaProvider, messagePayloadDeserializer);
    }
}
