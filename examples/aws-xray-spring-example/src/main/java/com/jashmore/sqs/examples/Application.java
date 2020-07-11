package com.jashmore.sqs.examples;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.config.DaemonConfiguration;
import com.amazonaws.xray.emitters.Emitter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.io.IOException;

@EnableScheduling
@SpringBootApplication
public class Application {
    static {
        final DaemonConfiguration daemonConfiguration = new DaemonConfiguration();
        daemonConfiguration.setDaemonAddress("127.0.0.1:2000");
        final AWSXRayRecorderBuilder builder;
        try {
            builder = AWSXRayRecorderBuilder.standard()
                    .withEmitter(Emitter.create(daemonConfiguration));
        } catch (IOException ioException) {
            throw new RuntimeException("Error setting up AWS Xray Daemon", ioException);
        }
        AWSXRay.setGlobalRecorder(builder.build());
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return SqsAsyncClient.create();
    }

    @Bean
    public SnsAsyncClient snsAsyncClient() {
        return SnsAsyncClient.create();
    }
}
