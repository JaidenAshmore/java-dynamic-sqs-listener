package it.com.jashmore.sqs.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.spring.queue.QueueResolverService;
import com.jashmore.sqs.test.LocalSqsRule;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import it.com.jashmore.example.Application;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(classes = {Application.class, EnvironmentQueueResolverServiceIntegrationTest.TestConfig.class}, webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {
        "property.with.queue.url=http://sqs.some.url",
        "property.with.queue.name=EnvironmentQueueResolverIntegrationTest"
})
@RunWith(SpringRunner.class)
public class EnvironmentQueueResolverServiceIntegrationTest {
    @ClassRule
    public static final LocalSqsRule LOCAL_SQS_RULE = new LocalSqsRule(ImmutableList.of(
            SqsQueuesConfig.QueueConfig.builder().queueName("EnvironmentQueueResolverIntegrationTest").build()
    ));

    @Autowired
    private QueueResolverService queueResolver;

    @Configuration
    public static class TestConfig {
        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return LOCAL_SQS_RULE.getLocalAmazonSqsAsync();
        }
    }

    @Test
    public void queueResolverResolvesVariablesFromEnvironmentProperties() {
        // act
        final String queueUrl = queueResolver.resolveQueueUrl(LOCAL_SQS_RULE.getLocalAmazonSqsAsync(), "${property.with.queue.url}");

        // assert
        assertThat(queueUrl).isEqualTo("http://sqs.some.url");
    }

    @Test
    public void queueResolverForQueueNameObtainsQueueUrlFromSqs() {
        // act
        final String queueUrl = queueResolver.resolveQueueUrl(LOCAL_SQS_RULE.getLocalAmazonSqsAsync(), "${property.with.queue.name}");

        // assert
        assertThat(queueUrl).startsWith(LOCAL_SQS_RULE.getServerUrl());
    }
}
