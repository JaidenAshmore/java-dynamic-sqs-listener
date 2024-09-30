package com.jashmore.sqs.micronaut.processor;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import com.jashmore.sqs.micronaut.decorator.MessageProcessingDecoratorFactory;
import com.jashmore.sqs.processor.MessageProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DecoratingMessageProcessorFactoryTest {

    @Mock
    SqsAsyncClient sqsAsyncClient;

    @Mock
    QueueProperties queueProperties;

    @Test
    void willAlwaysApplyGlobalDecoratorsIfPresent() throws Exception {
        // arrange
        final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
        final DecoratingMessageProcessorFactory factory = new DecoratingMessageProcessorFactory(
            Collections.singletonList(decorator),
            Collections.emptyList()
        );

        // act
        final MessageProcessor processor = factory.decorateMessageProcessor(
            sqsAsyncClient,
            "id",
            queueProperties,
            new MessageListener(),
            MessageListener.class.getMethod("actualListener"),
            (message, callback) -> CompletableFuture.completedFuture(null)
        );
        processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

        // assert
        verify(decorator).onPreMessageProcessing(any(), any());
    }

    @Test
    void willApplyDecoratosWhenReturnedFrom() throws Exception {
        // arrange
        final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
        final MessageProcessingDecoratorFactory<MessageProcessingDecorator> decoratorFactory = (
                sqsAsyncClient,
                queueProperties,
                identifier,
                bean,
                method
            ) ->
            Optional.of(decorator);

        final DecoratingMessageProcessorFactory factory = new DecoratingMessageProcessorFactory(
            Collections.emptyList(),
            Collections.singletonList(decoratorFactory)
        );

        // act
        final MessageProcessor processor = factory.decorateMessageProcessor(
            sqsAsyncClient,
            "id",
            queueProperties,
            new MessageListener(),
            MessageListener.class.getMethod("actualListener"),
            (message, callback) -> CompletableFuture.completedFuture(null)
        );
        processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

        // assert
        verify(decorator).onPreMessageProcessing(any(), any());
    }

    private static class MessageListener {

        public void actualListener() {}
    }
}
