package it.com.jashmore.sqs.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.jashmore.sqs.queue.QueueResolverService;
import it.com.jashmore.example.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {
        "property.with.queue.url=http://sqs.some.url",
        "property.with.queue.name=EnvironmentQueueResolverIntegrationTest"
})
@RunWith(SpringRunner.class)
public class EnvironmentQueueResolverServiceIntegrationTest {
    @Autowired
    private QueueResolverService queueResolver;

    @Test
    public void queueResolverResolvesVariablesFromEnvironmentProperties() throws InterruptedException {
        // act
        final String queueUrl = queueResolver.resolveQueueUrl("${property.with.queue.url}");

        // assert
        assertThat(queueUrl).isEqualTo("http://sqs.some.url");
    }

    @Test
    public void queueResolverForQueueNameObtainsQueueUrlFromSqs() throws InterruptedException {
        // act
        final String queueUrl = queueResolver.resolveQueueUrl("${property.with.queue.name}");

        // assert
        assertThat(queueUrl).startsWith("http://localhost:4576");
    }
}
