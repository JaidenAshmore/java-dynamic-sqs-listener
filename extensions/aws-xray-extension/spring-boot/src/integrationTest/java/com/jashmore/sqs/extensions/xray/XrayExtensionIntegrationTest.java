package com.jashmore.sqs.extensions.xray;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.config.DaemonConfiguration;
import com.amazonaws.xray.emitters.Emitter;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.extensions.xray.spring.SqsListenerXrayConfiguration;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = { XrayExtensionIntegrationTest.TestConfig.class, SqsListenerXrayConfiguration.class, QueueListenerConfiguration.class }
)
@TestPropertySource(properties = { "spring.application.name=my-test-service" })
public class XrayExtensionIntegrationTest {
    static final int XRAY_DAEMON_PORT = 4455;
    private static final String QUEUE_NAME = "XrayExtensionIntegrationTest";

    @Autowired
    public LocalSqsAsyncClient sqsAsyncClient;

    @Component
    static class TestConfig {

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @Bean
        @Qualifier("sqsXrayRecorder")
        public AWSXRayRecorder recorder() throws IOException {
            final DaemonConfiguration daemonConfiguration = new DaemonConfiguration();
            daemonConfiguration.setDaemonAddress("localhost:" + XRAY_DAEMON_PORT);
            final AWSXRayRecorder recorder = AWSXRayRecorderBuilder.standard().withEmitter(Emitter.create(daemonConfiguration)).build();
            AWSXRay.setGlobalRecorder(recorder);
            return recorder;
        }

        @Service
        public static class MessageListener {

            @SuppressWarnings("unused")
            @QueueListener(identifier = "my-identifier", value = QUEUE_NAME)
            public void listenToMessage() {}
        }
    }

    @Test
    void willSendSegmentAndSubsegmentForMessageListener()
        throws InterruptedException, SocketException, TimeoutException, ExecutionException {
        // arrange
        final DatagramSocket socket = new DatagramSocket(XRAY_DAEMON_PORT);
        final CompletableFuture<String> receiveDataFuture = CompletableFuture.supplyAsync(
            () -> {
                final byte[] buf = new byte[4096];
                final DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                } catch (IOException ioException) {
                    throw new RuntimeException(ioException);
                }
                socket.close();
                return new String(packet.getData(), 0, packet.getLength());
            }
        );

        // act
        sqsAsyncClient.sendMessage(QUEUE_NAME, "body");
        final String rawTracingData = receiveDataFuture.get(5, TimeUnit.SECONDS);

        // assert
        assertThat(rawTracingData).contains("\"name\":\"my-test-service\"");
        assertThat(rawTracingData).contains("\"name\":\"my-identifier\"");
    }
}
