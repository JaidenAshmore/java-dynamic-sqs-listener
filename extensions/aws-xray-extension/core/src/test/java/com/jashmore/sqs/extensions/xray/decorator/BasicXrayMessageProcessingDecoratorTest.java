package com.jashmore.sqs.extensions.xray.decorator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.entities.Entity;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.entities.TraceID;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.processor.DecoratingMessageProcessor;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

@ExtendWith(MockitoExtension.class)
class BasicXrayMessageProcessingDecoratorTest {
    @Mock
    private AWSXRayRecorder recorder;

    @Nested
    public class MessageListenerSegment {

        @Test
        void willBeginSegment() {
            // arrange
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options.builder().recorder(recorder).build()
            );
            final MessageProcessor processor = createMessageProcessor("message-listener", decorator);

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            verify(recorder).beginSegment(anyString());
        }

        @Test
        void whenNoSegmentNamingStrategyProvidedTheNameIsADefaultValue() {
            // arrange
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options.builder().recorder(recorder).build()
            );
            final MessageProcessor processor = createMessageProcessor("message-listener-identifier", decorator);

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            verify(recorder).beginSegment("message-listener");
        }

        @Test
        void whenNoRecorderGlobalRecorderIsUsed() {
            // arrange
            AWSXRay.setGlobalRecorder(recorder);
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options.builder().build()
            );
            final MessageProcessor processor = createMessageProcessor("message-listener-identifier", decorator);

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            verify(recorder).beginSegment("message-listener");
        }

        @Test
        void segmentNamingStrategyCanBeUsedToNameSegment() {
            // arrange
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options
                    .builder()
                    .recorder(recorder)
                    .segmentNamingStrategy(new StaticDecoratorSegmentNamingStrategy("static-name"))
                    .build()
            );
            final MessageProcessor processor = createMessageProcessor("message-listener-identifier", decorator);

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            verify(recorder).beginSegment("static-name");
        }

        @Test
        void segmentMutatorCanBeUsedToMutateTheSegmentAfterConstruction() {
            // arrange
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options
                    .builder()
                    .recorder(recorder)
                    .segmentMutator((segment, context, message) -> segment.setSampled(false))
                    .build()
            );
            final MessageProcessor processor = createMessageProcessor("message-listener-identifier", decorator);
            final Segment mockSegment = mock(Segment.class);
            when(recorder.beginSegment(anyString())).thenReturn(mockSegment);

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            verify(mockSegment).setSampled(false);
        }

        @Test
        void messageProcessingThreadWillHaveSegmentAsContextWhenNoSubsegmentCreated() {
            // arrange
            final AtomicReference<Segment> messageListenerSegment = new AtomicReference<>();
            final AtomicReference<Entity> messageProcessingEntity = new AtomicReference<>();
            final AWSXRayRecorder globalRecorder = AWSXRayRecorderBuilder.defaultRecorder();
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options
                    .builder()
                    .recorder(globalRecorder)
                    .segmentMutator((segment, context, message) -> messageListenerSegment.set(segment))
                    .generateSubsegment(false)
                    .build()
            );
            AWSXRay.setGlobalRecorder(globalRecorder);
            final MessageProcessor processor = createMessageProcessor(
                "message-listener-identifier",
                decorator,
                (message, resolver) -> {
                    messageProcessingEntity.set(AWSXRay.getCurrentSegment());
                    return CompletableFuture.completedFuture(null);
                }
            );

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            assertThat(messageProcessingEntity).hasValue(messageListenerSegment.get());
        }

        @Test
        void willContinueTraceWhenHeaderPresentInMessage() {
            // arrange
            final Segment mockSegment = mock(Segment.class);
            when(recorder.beginSegment(anyString())).thenReturn(mockSegment);
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options.builder().recorder(recorder).build()
            );
            final MessageProcessor processor = createMessageProcessor(
                "message-listener-identifier",
                decorator,
                (message, resolver) -> CompletableFuture.completedFuture(null)
            );

            // act
            processor.processMessage(
                Message
                    .builder()
                    .attributes(
                        Collections.singletonMap(
                            MessageSystemAttributeName.AWS_TRACE_HEADER,
                            "Root=1-5f07980f-7e310a9e3becb5a6386ce0e6;Parent=3ee6c885d5b1663b;Sampled=1"
                        )
                    )
                    .build(),
                () -> CompletableFuture.completedFuture(null)
            );

            // assert
            verify(mockSegment).setTraceId(TraceID.fromString("1-5f07980f-7e310a9e3becb5a6386ce0e6"));
            verify(mockSegment).setParentId("3ee6c885d5b1663b");
            verify(mockSegment).setSampled(true);
        }

        @Test
        void afterProcessingSegmentWillBeEnded() {
            // arrange
            final AtomicReference<Segment> messageListenerSegment = new AtomicReference<>();
            final AWSXRayRecorder globalRecorder = AWSXRayRecorderBuilder.defaultRecorder();
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options
                    .builder()
                    .recorder(globalRecorder)
                    .segmentMutator((segment, context, message) -> messageListenerSegment.set(segment))
                    .generateSubsegment(false)
                    .build()
            );
            AWSXRay.setGlobalRecorder(globalRecorder);
            final MessageProcessor processor = createMessageProcessor(
                "message-listener-identifier",
                decorator,
                (message, resolver) -> {
                    assertThat(messageListenerSegment.get().isEmitted()).isFalse();
                    return CompletableFuture.completedFuture(null);
                }
            );

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            assertThat(messageListenerSegment.get().isEmitted()).isTrue();
        }
    }

    @Nested
    class MessageListenerSubsegment {

        @Test
        void willNotCreateSubsegmentIfMarkedAsFalse() {
            // arrange
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options.builder().recorder(recorder).generateSubsegment(false).build()
            );
            final MessageProcessor processor = createMessageProcessor("message-listener", decorator);

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            verify(recorder, never()).beginSubsegment(anyString());
        }

        @Test
        void willCreateSubsegmentIfGenerateSubsegmentIsNull() {
            // arrange
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options.builder().recorder(recorder).generateSubsegment(null).build()
            );
            final MessageProcessor processor = createMessageProcessor("message-listener-identifier", decorator);

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            verify(recorder).beginSubsegment("message-listener-identifier");
        }

        @Test
        void whenNoSubsegmentNamingStrategySubsegmentNameWillBeMessageListenerIdentifier() {
            // arrange
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options.builder().recorder(recorder).subsegmentNamingStrategy(null).build()
            );
            final MessageProcessor processor = createMessageProcessor("message-listener-identifier", decorator);

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            verify(recorder).beginSubsegment("message-listener-identifier");
        }

        @Test
        void subsegmentNamingStrategyCanBeUsedToNameSubsegment() {
            // arrange
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options
                    .builder()
                    .recorder(recorder)
                    .subsegmentNamingStrategy((context, message) -> "static-name")
                    .build()
            );
            final MessageProcessor processor = createMessageProcessor("message-listener-identifier", decorator);

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            verify(recorder).beginSubsegment("static-name");
        }

        @Test
        void subsegmentMutatorCanBeUsedToModifyTheSubsegment() {
            // arrange
            final AtomicReference<Subsegment> subsegmentFound = new AtomicReference<>();
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options
                    .builder()
                    .recorder(recorder)
                    .subsegmentMutator(((subsegment, context, message) -> subsegmentFound.set(subsegment)))
                    .build()
            );
            final MessageProcessor processor = createMessageProcessor("message-listener-identifier", decorator);

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            assertThat(subsegmentFound).isNotNull();
        }

        @Test
        void messageProcessingThreadWillHaveSegmentAsContextWhenNoSubsegmentCreated() {
            // arrange
            final AtomicReference<Subsegment> messageListenerSubsegment = new AtomicReference<>();
            final AtomicReference<Entity> messageProcessingEntity = new AtomicReference<>();
            final AWSXRayRecorder globalRecorder = AWSXRayRecorderBuilder.defaultRecorder();
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options
                    .builder()
                    .recorder(globalRecorder)
                    .subsegmentMutator((segment, context, message) -> messageListenerSubsegment.set(segment))
                    .build()
            );
            AWSXRay.setGlobalRecorder(globalRecorder);
            final MessageProcessor processor = createMessageProcessor(
                "message-listener-identifier",
                decorator,
                (message, resolver) -> {
                    messageProcessingEntity.set(AWSXRay.getTraceEntity());
                    return CompletableFuture.completedFuture(null);
                }
            );

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            assertThat(messageProcessingEntity).hasValue(messageListenerSubsegment.get());
        }

        @Test
        void subsegmentWillBeClosedWhenFinishedProcessing() {
            // arrange
            final AtomicReference<Subsegment> messageListenerSubsegment = new AtomicReference<>();
            final AtomicReference<Entity> messageProcessingEntity = new AtomicReference<>();
            when(recorder.beginSegment(anyString())).thenReturn(mock(Segment.class));
            final Subsegment mockSubsegment = mock(Subsegment.class);
            when(recorder.beginSubsegment(anyString())).thenReturn(mockSubsegment);
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options
                    .builder()
                    .recorder(recorder)
                    .subsegmentMutator((segment, context, message) -> messageListenerSubsegment.set(segment))
                    .build()
            );
            final MessageProcessor processor = createMessageProcessor(
                "message-listener-identifier",
                decorator,
                (message, resolver) -> {
                    messageProcessingEntity.set(AWSXRay.getTraceEntity());
                    return CompletableFuture.completedFuture(null);
                }
            );

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            verify(recorder, times(2)).setTraceEntity(mockSubsegment);
            verify(recorder).endSubsegment();
        }
    }

    @Nested
    class ProcessingErrorOutcome {

        @Test
        void onFailureToProcessMessageSegmentWillHaveExceptionAddedToSegment() {
            // arrange
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options.builder().recorder(recorder).build()
            );
            final ExpectedTestException expectedTestException = new ExpectedTestException();
            final MessageProcessor processor = createMessageProcessor(
                "message-listener",
                decorator,
                CompletableFutureUtils.completedExceptionally(expectedTestException)
            );
            final Segment mockSegment = mock(Segment.class);
            when(recorder.beginSegment(anyString())).thenReturn(mockSegment);

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            verify(mockSegment).addException(expectedTestException);
        }

        @Test
        void onFailureToProcessMessageSegmentWillHaveExceptionAddedToSubsegment() {
            // arrange
            final BasicXrayMessageProcessingDecorator decorator = new BasicXrayMessageProcessingDecorator(
                BasicXrayMessageProcessingDecorator.Options.builder().recorder(recorder).build()
            );
            final ExpectedTestException expectedTestException = new ExpectedTestException();
            final MessageProcessor processor = createMessageProcessor(
                "message-listener",
                decorator,
                CompletableFutureUtils.completedExceptionally(expectedTestException)
            );
            final Subsegment mockSubsegment = mock(Subsegment.class);
            when(recorder.beginSubsegment(anyString())).thenReturn(mockSubsegment);

            // act
            processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

            // assert
            verify(mockSubsegment).addException(expectedTestException);
        }
    }

    private MessageProcessor createMessageProcessor(
        final String messageListenerIdentifier,
        final BasicXrayMessageProcessingDecorator decorator
    ) {
        return createMessageProcessor(messageListenerIdentifier, decorator, CompletableFuture.completedFuture(null));
    }

    private MessageProcessor createMessageProcessor(
        final String messageListenerIdentifier,
        final BasicXrayMessageProcessingDecorator decorator,
        final CompletableFuture<?> returnValue
    ) {
        return createMessageProcessor(messageListenerIdentifier, decorator, (message, resolveMessageCallback) -> returnValue);
    }

    private MessageProcessor createMessageProcessor(
        final String messageListenerIdentifier,
        final BasicXrayMessageProcessingDecorator decorator,
        final MessageProcessor delegate
    ) {
        return new DecoratingMessageProcessor(
            messageListenerIdentifier,
            QueueProperties.builder().queueUrl("url").build(),
            Collections.singletonList(decorator),
            delegate
        );
    }
}
