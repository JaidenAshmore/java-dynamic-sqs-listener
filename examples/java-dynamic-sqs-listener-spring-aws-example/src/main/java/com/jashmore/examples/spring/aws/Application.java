package com.jashmore.examples.spring.aws;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    @QueueListener("${sqs.queues.queueUrl}")
    public void messageListener(@Payload final String payload) {
        log.info("Payload: {}", payload);
    }
}
