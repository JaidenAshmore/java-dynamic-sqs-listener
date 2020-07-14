package com.jashmore.sqs.examples;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.config.DaemonConfiguration;
import com.amazonaws.xray.emitters.Emitter;
import org.springframework.beans.factory.annotation.Qualifier;
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
    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    @Bean
    @Qualifier("sqsXrayRecorder")
    public AWSXRayRecorder recorder() throws IOException {
        final DaemonConfiguration daemonConfiguration = new DaemonConfiguration();
        daemonConfiguration.setDaemonAddress("127.0.0.1:2000");
        final AWSXRayRecorder recorder = AWSXRayRecorderBuilder.standard()
                .withEmitter(Emitter.create(daemonConfiguration))
                .build();
        AWSXRay.setGlobalRecorder(recorder);
        return recorder;
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
