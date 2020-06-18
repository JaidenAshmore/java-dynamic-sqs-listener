package com.jashmore.sqs.extensions.brave.decorator;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.HashMap;
import java.util.Map;
import brave.ScopedSpan;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.test.TestSpanHandler;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.brave.propogation.SendMessageRemoteSetter;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.util.ExpectedTestException;
import org.junit.After;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

class BraveMessageProcessingDecoratorTest {
    private final TestSpanHandler spanHandler = new TestSpanHandler();
    private final Tracing tracing = Tracing.newBuilder()
            .addSpanHandler(spanHandler)
            .build();

    @After
    public void tearDown() {
        tracing.close();
    }

    @Test
    void onSupplySuccessWillStartAndStopSpan() {
        // arrange
        final Message message = Message.builder()
                .body("test")
                .build();
        final BraveMessageProcessingDecorator decorator = new BraveMessageProcessingDecorator(tracing);
        final MessageProcessingContext details = MessageProcessingContext.builder()
                .listenerIdentifier("identifier")
                .queueProperties(QueueProperties.builder().queueUrl("url").build())
                .attributes(new HashMap<>())
                .build();

        // act
        decorator.onPreSupply(details, message);
        assertThat(spanHandler.spans()).isEmpty();
        decorator.onSupplySuccess(details, message);

        // assert
        final MutableSpan span = spanHandler.get(0);
        assertThat(span.name()).isEqualTo("sqs-listener-identifier");
        assertThat(span.parentId()).isNull();
        assertThat(span.id()).isEqualTo(span.traceId());
    }

    @Test
    void onSupplyFailureWillStartAndStopSpan() {
        // arrange
        final Message message = Message.builder()
                .body("test")
                .build();
        final BraveMessageProcessingDecorator decorator = new BraveMessageProcessingDecorator(tracing);
        final MessageProcessingContext details = MessageProcessingContext.builder()
                .listenerIdentifier("identifier")
                .queueProperties(QueueProperties.builder().queueUrl("url").build())
                .attributes(new HashMap<>())
                .build();

        // act
        decorator.onPreSupply(details, message);
        assertThat(spanHandler.spans()).isEmpty();
        decorator.onSupplyFailure(details, message, new ExpectedTestException());

        // assert
        final MutableSpan span = spanHandler.get(0);
        assertThat(span.name()).isEqualTo("sqs-listener-identifier");
        assertThat(span.parentId()).isNull();
        assertThat(span.id()).isEqualTo(span.traceId());
        assertThat(span.error()).isInstanceOf(ExpectedTestException.class);
    }

    @Test
    void spanWillBeConsumableByProcessingMessage() {
        // arrange
        final Message message = Message.builder()
                .body("test")
                .build();
        final BraveMessageProcessingDecorator decorator = new BraveMessageProcessingDecorator(tracing);
        final MessageProcessingContext details = MessageProcessingContext.builder()
                .listenerIdentifier("identifier")
                .queueProperties(QueueProperties.builder().queueUrl("url").build())
                .attributes(new HashMap<>())
                .build();

        // act
        decorator.onPreSupply(details, message);
        tracing.tracer().startScopedSpan("span").finish();
        decorator.onSupplySuccess(details, message);

        // assert
        final MutableSpan childSpan = spanHandler.get(0);
        final MutableSpan processingSpan = spanHandler.get(1);
        assertThat(childSpan.name()).isEqualTo("span");
        assertThat(childSpan.parentId()).isEqualTo(processingSpan.id());
        assertThat(childSpan.traceId()).isEqualTo(processingSpan.traceId());
    }

    @Test
    void whenTracingInformationIsIncludedInMessageThatTraceIsContinued() {
        // arrange
        final ScopedSpan scopedSenderSpan = tracing.tracer().startScopedSpan("sender-span");
        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        SendMessageRemoteSetter.create(tracing).inject(scopedSenderSpan.context(), messageAttributes);
        final Message message = Message.builder()
                .body("test")
                .messageAttributes(messageAttributes)
                .build();
        final BraveMessageProcessingDecorator decorator = new BraveMessageProcessingDecorator(tracing);
        final MessageProcessingContext details = MessageProcessingContext.builder()
                .listenerIdentifier("identifier")
                .queueProperties(QueueProperties.builder().queueUrl("url").build())
                .attributes(new HashMap<>())
                .build();

        // act
        decorator.onPreSupply(details, message);
        decorator.onSupplySuccess(details, message);
        scopedSenderSpan.finish();

        // assert
        final MutableSpan messageProcessingSpan = spanHandler.get(0);
        final MutableSpan senderSpan = spanHandler.get(1);
        assertThat(messageProcessingSpan.parentId()).isEqualTo(senderSpan.id());
        assertThat(messageProcessingSpan.traceId()).isEqualTo(senderSpan.traceId());
    }

    @Test
    void noopSpansWillNotResultInAnySpansBeingTracked() {
        // arrange
        final Tracing tracing = Tracing.newBuilder().build();
        tracing.setNoop(true);
        final Message message = Message.builder()
                .body("test")
                .build();
        final BraveMessageProcessingDecorator decorator = new BraveMessageProcessingDecorator(tracing);
        final MessageProcessingContext details = MessageProcessingContext.builder()
                .listenerIdentifier("identifier")
                .queueProperties(QueueProperties.builder().queueUrl("url").build())
                .attributes(new HashMap<>())
                .build();

        // act
        decorator.onPreSupply(details, message);
        decorator.onSupplySuccess(details, message);

        // assert
        assertThat(spanHandler.spans()).isEmpty();
    }

    @Test
    void postProcessWillIgnoreIfNoSpansPresent() {
        // arrange
        final Tracing tracing = Tracing.newBuilder().build();
        tracing.setNoop(true);
        final Message message = Message.builder()
                .body("test")
                .build();
        final BraveMessageProcessingDecorator decorator = new BraveMessageProcessingDecorator(tracing);
        final MessageProcessingContext details = MessageProcessingContext.builder()
                .listenerIdentifier("identifier")
                .queueProperties(QueueProperties.builder().queueUrl("url").build())
                .attributes(new HashMap<>())
                .build();

        // act
        decorator.onSupplySuccess(details, message); // makes sure no exception is thrown
    }
}