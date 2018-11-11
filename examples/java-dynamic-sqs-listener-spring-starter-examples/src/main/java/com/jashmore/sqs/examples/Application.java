package com.jashmore.sqs.examples;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.jashmore.sqs.annotation.EnableQueueListeners;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@EnableQueueListeners
@SpringBootApplication
@ComponentScan("com.jashmore.sqs.examples")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    @Bean
    public AmazonSQSAsync amazonSqsAsync() {
        return new LocalAmazonSqsAsync();
    }
}
