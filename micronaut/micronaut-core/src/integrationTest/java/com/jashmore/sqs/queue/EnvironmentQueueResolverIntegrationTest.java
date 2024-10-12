package com.jashmore.sqs.queue;

import com.jashmore.sqs.client.QueueResolver;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest(environments = "EnvironmentQueueResolverIntegrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "property.with.queue.url", value = "http://sqs.some.url")
@Property(name = "property.with.queue.name", value = "EnvironmentQueueResolverIntegrationTest")
class EnvironmentQueueResolverIntegrationTest {

    private static final String QUEUE_NAME = "EnvironmentQueueResolverIntegrationTest";

    @Inject
    private QueueResolver queueResolver;

    @Inject
    private ElasticMqSqsAsyncClient localSqsAsyncClient;

    @Factory
    @Requires(env = "EnvironmentQueueResolverIntegrationTest")
    public static class TestConfig {

        @Singleton
        public ElasticMqSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }
    }

    @Test
    void queueResolverResolvesVariablesFromEnvironmentProperties() {
        // act
        final String queueUrl = queueResolver.resolveQueueUrl(localSqsAsyncClient, "${property.with.queue.url}");

        // assert
        assertThat(queueUrl).isEqualTo("http://sqs.some.url");
    }

    @Test
    void queueResolverForQueueNameObtainsQueueUrlFromSqs() {
        // act
        final String queueUrl = queueResolver.resolveQueueUrl(localSqsAsyncClient, "${property.with.queue.name}");

        // assert
        assertThat(queueUrl).startsWith(localSqsAsyncClient.getServerUrl());
    }
}
