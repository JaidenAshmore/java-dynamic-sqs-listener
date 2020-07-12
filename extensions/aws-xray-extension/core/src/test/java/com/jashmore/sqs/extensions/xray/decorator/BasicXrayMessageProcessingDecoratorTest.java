package com.jashmore.sqs.extensions.xray.decorator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.TraceID;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.util.Collections;
import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
class BasicXrayMessageProcessingDecoratorTest {

    @Mock
    private AWSXRayRecorder recorder;

    private BasicXrayMessageProcessingDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new BasicXrayMessageProcessingDecorator(recorder, new StaticDecoratorSegmentNamingStrategy("name"));
    }

    @Nested
    class OnPreMessageProcessing {

        @Test
        void willBeginSegmentBeforeSupplyingMessageForProcessing() {
            // arrange
            final MessageProcessingContext context = MessageProcessingContext.builder()
                    .listenerIdentifier("identifier")
                    .queueProperties(QueueProperties.builder().queueUrl("url").build())
                    .attributes(new HashMap<>())
                    .build();

            // act
            decorator.onPreMessageProcessing(context, Message.builder().build());

            // assert
            verify(recorder).beginSegment("name");
        }

        @Test
        void willContinueTraceWhenHeaderPresentInMessage() {
            // arrange
            final MessageProcessingContext context = MessageProcessingContext.builder()
                    .listenerIdentifier("identifier")
                    .queueProperties(QueueProperties.builder().queueUrl("url").build())
                    .attributes(new HashMap<>())
                    .build();
            final Segment mockSegment = mock(Segment.class);
            when(recorder.beginSegment("name")).thenReturn(mockSegment);

            // act
            decorator.onPreMessageProcessing(context, Message.builder()
                    .attributes(Collections.singletonMap(
                            MessageSystemAttributeName.AWS_TRACE_HEADER, "Root=1-5f07980f-7e310a9e3becb5a6386ce0e6;Parent=3ee6c885d5b1663b;Sampled=1")
                    )
                    .build());

            // assert
            verify(mockSegment).setTraceId(TraceID.fromString("1-5f07980f-7e310a9e3becb5a6386ce0e6"));
            verify(mockSegment).setParentId("3ee6c885d5b1663b");
            verify(mockSegment).setSampled(true);
        }
    }

    @Test
    void whenSupplyingTheMessageHasFinishedTheSegmentWillBeEnded() {
        // arrange
        final MessageProcessingContext context = MessageProcessingContext.builder()
                .listenerIdentifier("identifier")
                .queueProperties(QueueProperties.builder().queueUrl("url").build())
                .attributes(new HashMap<>())
                .build();

        // act
        decorator.onMessageProcessingThreadComplete(context, Message.builder().build());

        // assert
        verify(recorder).endSegment();
    }
}