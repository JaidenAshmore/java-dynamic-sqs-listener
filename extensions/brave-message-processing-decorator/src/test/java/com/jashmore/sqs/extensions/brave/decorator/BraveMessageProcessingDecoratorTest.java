package com.jashmore.sqs.extensions.brave.decorator;

import static org.assertj.core.api.Assertions.assertThat;

import brave.ScopedSpan;
import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.test.TestSpanHandler;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.brave.propogation.SendMessageRemoteGetter;
import com.jashmore.sqs.brave.propogation.SendMessageRemoteSetter;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.util.ExpectedTestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.HashMap;
import java.util.Map;

class BraveMessageProcessingDecoratorTest {
    private final TestSpanHandler spanHandler = new TestSpanHandler();
    private ThreadLocalCurrentTraceContext currentTraceContext;
    private Tracing tracing;

    private final Message message = Message.builder()
            .body("test")
            .build();

    private BraveMessageProcessingDecorator decorator;

    @BeforeEach
    void setUp() {
        currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder().build();
        tracing = Tracing.newBuilder()
                .addSpanHandler(spanHandler)
                .currentTraceContext(currentTraceContext)
                .build();
        decorator = new BraveMessageProcessingDecorator(tracing);
    }

    @AfterEach
    public void tearDown() {
        currentTraceContext.clear();
        tracing.close();
    }

    @Nested
    class PreMessageProcessing {
        @Test
        void willCreateSpanAndSaveToContext() {
            // arrange
            final MessageProcessingContext context = newContext();

            // act
            decorator.onPreMessageProcessing(context, message);

            // assert
            final Object span = context.getAttribute("BraveMessageProcessingDecorator:span");
            assertThat(span).isInstanceOf(Span.class);
        }

        @Test
        void willStartTheSpanInScope() {
            // arrange
            final MessageProcessingContext context = newContext();

            // act
            assertThat(tracing.currentTraceContext().get()).isNull();
            decorator.onPreMessageProcessing(context, message);

            // assert
            assertThat(tracing.currentTraceContext().get()).isNotNull();
        }

        @Test
        void willPersistSpanInScopeIntoContextSoItCanBeClosedLater() {
            // arrange
            final MessageProcessingContext context = newContext();

            // act
            decorator.onPreMessageProcessing(context, message);

            // assert
            final Object span = context.getAttribute("BraveMessageProcessingDecorator:span-in-scope");
            assertThat(span).isInstanceOf(Tracer.SpanInScope.class);
        }

        @Test
        void noopSpansWillNotResultInAnySpansBeingTracked() {
            // arrange
            final MessageProcessingContext context = newContext();
            tracing.setNoop(true);

            // act
            decorator.onPreMessageProcessing(context, message);

            // assert
            final Object span = context.getAttribute("BraveMessageProcessingDecorator:span");
            assertThat(span).isNull();
        }
    }

    @Nested
    class OnMessageProcessingThreadComplete {
        @Test
        void willDoNothingIfNoSpanInScopePresent() {
            // arrange
            final MessageProcessingContext context = newContext();
            decorator.onPreMessageProcessing(context, message);

            // act
            context.setAttribute("BraveMessageProcessingDecorator:span-in-scope", null);
            decorator.onMessageProcessingThreadComplete(context, message);

            // assert not exception thrown
        }

        @Test
        void willCloseSpanInScopeWhenPresent() {
            // arrange
            final MessageProcessingContext context = newContext();
            decorator.onPreMessageProcessing(context, message);
            assertThat(currentTraceContext.get()).isNotNull();

            // act
            decorator.onMessageProcessingThreadComplete(context, message);

            // assert
            assertThat(currentTraceContext.get()).isNull();
        }
    }

    @Nested
    class OnMessageProcessingSuccess {
        @Test
        void willFinishTheSpan() {
            // arrange
            final MessageProcessingContext context = newContext();
            decorator.onPreMessageProcessing(context, message);

            // act
            assertThat(spanHandler.spans()).isEmpty();
            decorator.onMessageProcessingSuccess(context, message, null);

            // assert
            assertThat(spanHandler.spans()).hasSize(1);
        }

        @Test
        void willNotFinishTheSpanIfNotPresent() {
            // arrange
            final MessageProcessingContext context = newContext();
            decorator.onPreMessageProcessing(context, message);

            // act
            context.setAttribute("BraveMessageProcessingDecorator:span", null);
            decorator.onMessageProcessingSuccess(context, message, null);

            // assert
            assertThat(spanHandler.spans()).isEmpty();
        }
    }

    @Nested
    class OnMessageProcessingFailure {
        @Test
        void willFinishTheSpan() {
            // arrange
            final MessageProcessingContext context = newContext();
            decorator.onPreMessageProcessing(context, message);

            // act
            assertThat(spanHandler.spans()).isEmpty();
            decorator.onMessageProcessingFailure(context, message, new ExpectedTestException());

            // assert
            assertThat(spanHandler.spans()).hasSize(1);
            assertThat(spanHandler.spans().get(0).error()).isInstanceOf(ExpectedTestException.class);
        }

        @Test
        void willNotFinishTheSpanIfNotPresent() {
            // arrange
            final MessageProcessingContext context = newContext();
            decorator.onPreMessageProcessing(context, message);

            // act
            context.setAttribute("BraveMessageProcessingDecorator:span", null);
            decorator.onMessageProcessingSuccess(context, message, null);

            // assert
            assertThat(spanHandler.spans()).isEmpty();
        }
    }

    @Nested
    class Options {
        @Nested
        class SpanNameCreator {
            @Test
            void canBeUsedToManuallySetSpanName() {
                // arrange
                final BraveMessageProcessingDecorator.Options options = BraveMessageProcessingDecorator.Options.builder()
                        .spanNameCreator((context, message) -> "my-span-name")
                        .build();
                decorator = new BraveMessageProcessingDecorator(tracing, options);
                final MessageProcessingContext context = newContext();

                // act
                decorator.onPreMessageProcessing(context, message);
                decorator.onMessageProcessingSuccess(context, message, null);

                // assert
                assertThat(spanHandler.spans().get(0).name()).isEqualTo("my-span-name");
            }
        }

        @Nested
        class TraceExtractor {
            @Test
            void canBeUsedToManuallySetToProvideMethodToExtractSpanDetails() {
                // arrange
                final ScopedSpan scopedSenderSpan = tracing.tracer().startScopedSpan("sender-span");
                final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
                SendMessageRemoteSetter.create(tracing).inject(scopedSenderSpan.context(), messageAttributes);
                final Message message = Message.builder()
                        .body("test")
                        .messageAttributes(messageAttributes)
                        .build();
                final MessageProcessingContext context = newContext();
                final BraveMessageProcessingDecorator.Options options = BraveMessageProcessingDecorator.Options.builder()
                        .traceExtractor(SendMessageRemoteGetter.create(tracing))
                        .build();
                decorator = new BraveMessageProcessingDecorator(tracing, options);

                // act
                decorator.onPreMessageProcessing(context, message);
                decorator.onMessageProcessingSuccess(context, message, null);
                scopedSenderSpan.finish();

                // assert
                final MutableSpan messageProcessingSpan = spanHandler.get(0);
                final MutableSpan senderSpan = spanHandler.get(1);
                assertThat(messageProcessingSpan.parentId()).isEqualTo(senderSpan.id());
                assertThat(messageProcessingSpan.traceId()).isEqualTo(senderSpan.traceId());
            }
        }
    }


    @Test
    void spanWillBeConsumableByProcessingMessage() {
        // arrange
        final MessageProcessingContext context = newContext();

        // act
        decorator.onPreMessageProcessing(context, message);
        tracing.tracer().startScopedSpan("child-span").finish();
        tracing.tracer().startScopedSpan("another-child-span").finish();
        decorator.onMessageProcessingSuccess(context, message, null);

        // assert
        final MutableSpan childSpan = spanHandler.get(0);
        final MutableSpan anotherChildSpan = spanHandler.get(1);
        final MutableSpan processingSpan = spanHandler.get(2);
        assertThat(childSpan.name()).isEqualTo("child-span");
        assertThat(anotherChildSpan.name()).isEqualTo("another-child-span");
        assertThat(processingSpan.name()).isEqualTo("sqs-listener-identifier");
        assertThat(childSpan.parentId()).isEqualTo(processingSpan.id());
        assertThat(anotherChildSpan.parentId()).isEqualTo(processingSpan.id());
        assertThat(childSpan.traceId()).isEqualTo(processingSpan.traceId());
        assertThat(anotherChildSpan.traceId()).isEqualTo(processingSpan.traceId());
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
        final MessageProcessingContext context = newContext();

        // act
        decorator.onPreMessageProcessing(context, message);
        decorator.onMessageProcessingSuccess(context, message, null);
        scopedSenderSpan.finish();

        // assert
        final MutableSpan messageProcessingSpan = spanHandler.get(0);
        final MutableSpan senderSpan = spanHandler.get(1);
        assertThat(messageProcessingSpan.parentId()).isEqualTo(senderSpan.id());
        assertThat(messageProcessingSpan.traceId()).isEqualTo(senderSpan.traceId());
    }

    private static MessageProcessingContext newContext() {
        return MessageProcessingContext.builder()
                .listenerIdentifier("identifier")
                .queueProperties(QueueProperties.builder().queueUrl("url").build())
                .attributes(new HashMap<>())
                .build();
    }
}