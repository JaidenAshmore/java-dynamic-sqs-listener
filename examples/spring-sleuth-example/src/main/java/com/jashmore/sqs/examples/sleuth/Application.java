package com.jashmore.sqs.examples.sleuth;

import brave.Tracing;
import com.jashmore.sqs.brave.SendMessageBatchTracingExecutionInterceptor;
import com.jashmore.sqs.brave.SendMessageTracingExecutionInterceptor;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Slf4j
@SpringBootApplication
public class Application {
    /**
     * Connects to an internal ElasticMQ SQS Server.
     *
     * @param tracing brave tracing for tracing the request
     * @return client used for communicating a local in-memory SQS
     */
    @Bean
    public SqsAsyncClient sqsAsyncClient(Tracing tracing) {
        return new ElasticMqSqsAsyncClient("test", (sqsAsyncClientBuilder) ->
                sqsAsyncClientBuilder.overrideConfiguration(overrideConfigurationBuilder -> overrideConfigurationBuilder
                        .addExecutionInterceptor(new SendMessageTracingExecutionInterceptor(tracing))
                        .addExecutionInterceptor(new SendMessageBatchTracingExecutionInterceptor(tracing))
                ));
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
