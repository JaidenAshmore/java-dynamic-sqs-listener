package it.com.jashmore.sqs.brave;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.scaladsl.Http;
import brave.ScopedSpan;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.test.TestSpanHandler;
import com.jashmore.sqs.brave.SendMessageBatchTracingExecutionInterceptor;
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
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SendMessageBatchTracingExecutionInterceptorIntegrationTest {
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
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("accessKeyId", "secretAccessKey")))
                .overrideConfiguration(builder -> builder.addExecutionInterceptor(new SendMessageBatchTracingExecutionInterceptor(tracing)))
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
        testSpanHandler.clear();
    }

    @Test
    public void spanInformationIsSharedOverSqsMessageAttributes() throws InterruptedException, TimeoutException, ExecutionException {
        // arrange
        final ScopedSpan scopedSpan = tracing.tracer().startScopedSpan("test-span");
        final List<SendMessageBatchRequestEntry> messages = new ArrayList<>();
        messages.add(SendMessageBatchRequestEntry.builder()
                .id("first")
                .messageBody("body-first")
                .build());
        messages.add(SendMessageBatchRequestEntry.builder()
                .id("second")
                .messageBody("body-second")
                .build());

        // act
        sqsAsyncClient.sendMessageBatch(builder -> builder.queueUrl(queueUrl).entries(messages)).get(5, TimeUnit.SECONDS);

        final ReceiveMessageResponse receiveMessageResponse =
                sqsAsyncClient.receiveMessage(builder -> builder
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(2)
                        .waitTimeSeconds(5)
                        .messageAttributeNames("b3")
                ).get(5, TimeUnit.SECONDS);
        scopedSpan.finish();

        // assert
        final MutableSpan firstMessageSpan = testSpanHandler.spans().get(0);
        final MutableSpan secondMessageSpan = testSpanHandler.spans().get(1);
        final MutableSpan testSpan = testSpanHandler.spans().get(2);

        final Message firstMessage = getMessage(receiveMessageResponse, firstMessageSpan.tag("message.id"));
        final TraceContextOrSamplingFlags firstMessageContext = SendMessageRemoteGetter.create(tracing).extract(firstMessage.messageAttributes());
        assertThat(firstMessageContext.context().traceIdString()).isEqualTo(testSpan.traceId());
        assertThat(firstMessageContext.context().traceIdString()).isEqualTo(firstMessageSpan.traceId());
        assertThat(firstMessageContext.context().spanIdString()).isEqualTo(firstMessageSpan.id());

        final Message secondMessage = getMessage(receiveMessageResponse, secondMessageSpan.tag("message.id"));
        final TraceContextOrSamplingFlags secondMessageContext = SendMessageRemoteGetter.create(tracing).extract(secondMessage.messageAttributes());
        assertThat(secondMessageContext.context().traceIdString()).isEqualTo(testSpan.traceId());
        assertThat(secondMessageContext.context().traceIdString()).isEqualTo(secondMessageSpan.traceId());
        assertThat(secondMessageContext.context().spanIdString()).isEqualTo(secondMessageSpan.id());
    }

    private Message getMessage(final ReceiveMessageResponse response, final String messageId) {
        return response.messages().stream()
                .filter(message -> message.messageId().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find message with ID: " + messageId));
    }
}
