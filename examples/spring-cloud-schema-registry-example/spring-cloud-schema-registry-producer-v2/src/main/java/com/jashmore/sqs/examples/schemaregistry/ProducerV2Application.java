package com.jashmore.sqs.examples.schemaregistry;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.registry.AvroSchemaRegistrySqsAsyncClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.schema.registry.avro.AvroMessageConverterProperties;
import org.springframework.cloud.schema.registry.avro.AvroSchemaServiceManager;
import org.springframework.cloud.schema.registry.client.EnableSchemaRegistryClient;
import org.springframework.cloud.schema.registry.client.SchemaRegistryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
@EnableScheduling
@EnableSchemaRegistryClient
@SuppressWarnings("checkstyle:javadocmethod")
public class ProducerV2Application {

    public static void main(String[] args) {
        SpringApplication.run(ProducerV2Application.class);
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return SqsAsyncClient.builder()
                .endpointOverride(URI.create("http://localhost:9324"))
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("X", "X")))
                .build();
    }

    @Bean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public AvroSchemaRegistrySqsAsyncClient avroSchemaRegistrySqsAsyncClient(final SqsAsyncClient delegate,
                                                                             final SchemaRegistryClient schemaRegistryClient,
                                                                             final AvroSchemaServiceManager avroSchemaServiceManager,
                                                                             final AvroMessageConverterProperties avroMessageConverterProperties) {
        final List<Resource> schemaImports = Optional.ofNullable(avroMessageConverterProperties.getSchemaImports())
                .map(ImmutableList::copyOf)
                .orElse(ImmutableList.of());
        final List<Resource> schemaLocations = Optional.ofNullable(avroMessageConverterProperties.getSchemaLocations())
                .map(ImmutableList::copyOf)
                .orElse(ImmutableList.of());

        return new AvroSchemaRegistrySqsAsyncClient(delegate, schemaRegistryClient, avroSchemaServiceManager, schemaImports, schemaLocations);
    }
}
