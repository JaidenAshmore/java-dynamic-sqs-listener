package com.jashmore.sqs.extensions.xray.decorator;

import static org.mockito.Mockito.verify;

import com.amazonaws.xray.AWSXRayRecorder;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
class BasicXrayMessageProcessingDecoratorTest {

    @Mock
    private AWSXRayRecorder recorder;

    private BasicXrayMessageProcessingDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new BasicXrayMessageProcessingDecorator(recorder);
    }

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
        verify(recorder).beginSegment("sqs-listener-identifier");
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