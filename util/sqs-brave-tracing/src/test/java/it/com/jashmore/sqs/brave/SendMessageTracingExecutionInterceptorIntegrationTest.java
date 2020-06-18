package it.com.jashmore.sqs.brave;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import akka.http.scaladsl.Http;
import brave.ScopedSpan;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.test.TestSpanHandler;
import com.jashmore.sqs.brave.SendMessageTracingExecutionInterceptor;
import com.jashmore.sqs.brave.propogation.SendMessageRemoteGetter;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

public class SendMessageTracingExecutionInterceptorIntegrationTest {
    private final TestSpanHandler testSpanHandler = new TestSpanHandler();
    private final Tracing tracing = Tracing.newBuilder()
            .addSpanHandler(testSpanHandler)
            .build();

    private SqsAsyncClient sqsAsyncClient;

    private SQSRestServer sqsRestServer;

    private String queueUrl;

    @BeforeEach
    public void setUp() throws Exception {
        sqsRestServer = SQSRestServerBuilder
                .withInterface("localhost")
                .withDynamicPort()
                .start();

        final Http.ServerBinding serverBinding = sqsRestServer.waitUntilStarted();
        final String queueServerUrl = "http://localhost:" + serverBinding.localAddress().getPort();

        sqsAsyncClient = SqsAsyncClient.builder()
                .endpointOverride(URI.create(queueServerUrl))
                .region(Region.of("elastic-mq"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("accessKeyId", "secretAccessKey")))
                .overrideConfiguration(builder -> builder.addExecutionInterceptor(
                        new SendMessageTracingExecutionInterceptor(tracing)))
                .build();

        queueUrl = sqsAsyncClient.createQueue(builder -> builder.queueName("name"))
                .get(5, TimeUnit.SECONDS)
                .queueUrl();
    }

    @AfterEach
    public void tearDown() {
        if (sqsRestServer != null) {
            sqsRestServer.stopAndWait();
        }
    }

    @Test
    public void spanInformationIsSharedOverSqsMessageAttributes() throws Exception {
        // arrange
        final ScopedSpan scopedSpan = tracing.tracer().startScopedSpan("test-span");

        // act
        final String messageId =
                sqsAsyncClient.sendMessage(builder -> builder.queueUrl(queueUrl).messageBody("body"))
                        .get(5, TimeUnit.SECONDS)
                        .messageId();

        final ReceiveMessageResponse receiveMessageResponse =
                sqsAsyncClient.receiveMessage(builder -> builder
                        .queueUrl(queueUrl)
                        .waitTimeSeconds(5)
                        .messageAttributeNames("b3")
                ).get(5, TimeUnit.SECONDS);
        scopedSpan.finish();

        // assert
        assertThat(receiveMessageResponse.messages()).hasSize(1);
        final Message message = receiveMessageResponse.messages().get(0);
        assertThat(message.messageId()).isEqualTo(messageId);
        final TraceContextOrSamplingFlags traceContextOrSamplingFlags =
                SendMessageRemoteGetter.create(tracing).extract(message.messageAttributes());
        final MutableSpan sendMessageSpan = testSpanHandler.spans().get(0);
        final MutableSpan testSpan = testSpanHandler.spans().get(1);
        assertThat(traceContextOrSamplingFlags.context().traceIdString()).isEqualTo(
                sendMessageSpan.traceId());
        assertThat(traceContextOrSamplingFlags.context().traceIdString()).isEqualTo(testSpan.traceId());
    }
}
