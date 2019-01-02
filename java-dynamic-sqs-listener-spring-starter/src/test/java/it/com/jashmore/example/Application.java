package it.com.jashmore.example;

import com.jashmore.sqs.annotation.EnableQueueListeners;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Slf4j
@EnableQueueListeners
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return new LocalSqsAsyncClient(SqsQueuesConfig
                .builder()
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName("QueueListenerWrapperIntegrationTest").build())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName("CustomQueueWrapperIntegrationTest").build())
                .queue(SqsQueuesConfig.QueueConfig.builder().queueName("EnvironmentQueueResolverIntegrationTest").build())
                .build());
    }
}
