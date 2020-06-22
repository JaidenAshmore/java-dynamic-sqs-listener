package it.com.jashmore.sqs.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.spring.queue.QueueResolver;
import com.jashmore.sqs.test.LocalSqsExtension;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import it.com.jashmore.example.Application;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest(classes = {Application.class, EnvironmentQueueResolverIntegrationTest.TestConfig.class})
@TestPropertySource(properties = {
        "property.with.queue.url=http://sqs.some.url",
        "property.with.queue.name=EnvironmentQueueResolverIntegrationTest"
})
@ExtendWith(SpringExtension.class)
class EnvironmentQueueResolverIntegrationTest {
    private static final String QUEUE_NAME = "EnvironmentQueueResolverIntegrationTest";

    @RegisterExtension
    public static final LocalSqsExtension LOCAL_SQS_RULE = new LocalSqsExtension(QUEUE_NAME);

    @Autowired
    private QueueResolver queueResolver;

    @Configuration
    public static class TestConfig {
        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return LOCAL_SQS_RULE.getLocalAmazonSqsAsync();
        }
    }

    @Test
    void queueResolverResolvesVariablesFromEnvironmentProperties() {
        // act
        final String queueUrl = queueResolver.resolveQueueUrl(LOCAL_SQS_RULE.getLocalAmazonSqsAsync(), "${property.with.queue.url}");

        // assert
        assertThat(queueUrl).isEqualTo("http://sqs.some.url");
    }

    @Test
    void queueResolverForQueueNameObtainsQueueUrlFromSqs() {
        // act
        final String queueUrl = queueResolver.resolveQueueUrl(LOCAL_SQS_RULE.getLocalAmazonSqsAsync(), "${property.with.queue.name}");

        // assert
        assertThat(queueUrl).startsWith(LOCAL_SQS_RULE.getServerUrl());
    }
}
