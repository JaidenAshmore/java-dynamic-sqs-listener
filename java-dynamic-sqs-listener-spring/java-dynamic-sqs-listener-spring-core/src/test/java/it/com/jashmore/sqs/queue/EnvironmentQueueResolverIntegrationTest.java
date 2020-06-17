package it.com.jashmore.sqs.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.spring.queue.QueueResolver;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest(classes = {EnvironmentQueueResolverIntegrationTest.TestConfig.class, QueueListenerConfiguration.class})
@TestPropertySource(properties = {
        "property.with.queue.url=http://sqs.some.url",
        "property.with.queue.name=EnvironmentQueueResolverIntegrationTest"
})
@ExtendWith(SpringExtension.class)
class EnvironmentQueueResolverIntegrationTest {
    private static final String QUEUE_NAME = "EnvironmentQueueResolverIntegrationTest";

    @Autowired
    private QueueResolver queueResolver;

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @Configuration
    public static class TestConfig {
        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
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
