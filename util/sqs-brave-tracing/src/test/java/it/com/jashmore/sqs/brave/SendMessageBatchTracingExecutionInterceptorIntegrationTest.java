package it.com.jashmore.sqs.brave;

import static org.assertj.core.api.Assertions.assertThat;

import brave.ScopedSpan;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.test.TestSpanHandler;
import com.jashmore.sqs.brave.SendMessageBatchTracingExecutionInterceptor;
import com.jashmore.sqs.brave.propogation.SendMessageRemoteGetter;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

public class SendMessageBatchTracingExecutionInterceptorIntegrationTest {
    private final TestSpanHandler testSpanHandler = new TestSpanHandler();
    private final Tracing tracing = Tracing.newBuilder().addSpanHandler(testSpanHandler).build();

    private LocalSqsAsyncClient sqsAsyncClient;

    private String queueUrl;

    @BeforeEach
    @SneakyThrows
    public void setUp() {
        sqsAsyncClient =
            new ElasticMqSqsAsyncClient(
                builder -> {
                    builder.overrideConfiguration(
                        overrideBuilder -> overrideBuilder.addExecutionInterceptor(new SendMessageBatchTracingExecutionInterceptor(tracing))
                    );
                }
            );

        queueUrl = sqsAsyncClient.createRandomQueue().get(5, TimeUnit.SECONDS).getResponse().queueUrl();
    }

    @AfterEach
    public void tearDown() {
        sqsAsyncClient.close();
        testSpanHandler.clear();
    }

    @Test
    public void spanInformationIsSharedOverSqsMessageAttributes() throws InterruptedException, TimeoutException, ExecutionException {
        // arrange
        final ScopedSpan scopedSpan = tracing.tracer().startScopedSpan("test-span");
        final List<SendMessageBatchRequestEntry> messages = new ArrayList<>();
        messages.add(SendMessageBatchRequestEntry.builder().id("first").messageBody("body-first").build());
        messages.add(SendMessageBatchRequestEntry.builder().id("second").messageBody("body-second").build());

        // act
        sqsAsyncClient.sendMessageBatch(builder -> builder.queueUrl(queueUrl).entries(messages)).get(5, TimeUnit.SECONDS);

        final ReceiveMessageResponse receiveMessageResponse = sqsAsyncClient
            .receiveMessage(builder -> builder.queueUrl(queueUrl).maxNumberOfMessages(2).waitTimeSeconds(5).messageAttributeNames("b3"))
            .get(5, TimeUnit.SECONDS);
        scopedSpan.finish();

        // assert
        final MutableSpan firstMessageSpan = testSpanHandler.spans().get(0);
        final MutableSpan secondMessageSpan = testSpanHandler.spans().get(1);
        final MutableSpan testSpan = testSpanHandler.spans().get(2);

        final Message firstMessage = getMessage(receiveMessageResponse, firstMessageSpan.tag("message.id"));
        final TraceContextOrSamplingFlags firstMessageContext = SendMessageRemoteGetter
            .create(tracing)
            .extract(firstMessage.messageAttributes());
        assertThat(firstMessageContext.context().traceIdString()).isEqualTo(testSpan.traceId());
        assertThat(firstMessageContext.context().traceIdString()).isEqualTo(firstMessageSpan.traceId());
        assertThat(firstMessageContext.context().spanIdString()).isEqualTo(firstMessageSpan.id());

        final Message secondMessage = getMessage(receiveMessageResponse, secondMessageSpan.tag("message.id"));
        final TraceContextOrSamplingFlags secondMessageContext = SendMessageRemoteGetter
            .create(tracing)
            .extract(secondMessage.messageAttributes());
        assertThat(secondMessageContext.context().traceIdString()).isEqualTo(testSpan.traceId());
        assertThat(secondMessageContext.context().traceIdString()).isEqualTo(secondMessageSpan.traceId());
        assertThat(secondMessageContext.context().spanIdString()).isEqualTo(secondMessageSpan.id());
    }

    private Message getMessage(final ReceiveMessageResponse response, final String messageId) {
        return response
            .messages()
            .stream()
            .filter(message -> message.messageId().equals(messageId))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Could not find message with ID: " + messageId));
    }
}
