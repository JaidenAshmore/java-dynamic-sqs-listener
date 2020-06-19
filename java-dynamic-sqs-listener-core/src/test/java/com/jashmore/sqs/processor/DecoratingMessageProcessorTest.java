package com.jashmore.sqs.processor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.Message;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecoratingMessageProcessorTest {

    QueueProperties queueProperties = QueueProperties.builder().build();

    MessageProcessingContext emptyContext = MessageProcessingContext.builder()
            .listenerIdentifier("identifier")
            .queueProperties(queueProperties)
            .attributes(new HashMap<>())
            .build();

    Message message = Message.builder().body("body").build();

    @Mock
    MessageProcessor delegate;

    @Mock
    Supplier<CompletableFuture<?>> mockMessageResolver;

    @Nested
    class Supply {
        @Test
        void decoratorsRunOnThreadProcessingMessage() {
            // arrange
            final Thread messageProcessingThread = Thread.currentThread();
            final AtomicReference<Thread> preProcessorThread = new AtomicReference<>();
            final AtomicReference<Thread> processorThread = new AtomicReference<>();
            final MessageProcessingDecorator decorator = new MessageProcessingDecorator() {
                @Override
                public void onPreSupply(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                    preProcessorThread.set(Thread.currentThread());
                }
            };
            final MessageProcessor delegate = (message, resolveMessageCallback) -> {
                processorThread.set(Thread.currentThread());
                return CompletableFuture.completedFuture(null);
            };
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties, ImmutableList.of(decorator), delegate);

            // act
            processor.processMessage(message, mockMessageResolver);

            // assert
            assertThat(preProcessorThread.get()).isSameAs(messageProcessingThread);
            assertThat(processorThread.get()).isSameAs(messageProcessingThread);
        }

        @Test
        void anyFailingPreProcessWillNotProcessTheMessage() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            doThrow(RuntimeException.class).when(decorator).onPreSupply(any(), any());
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator), delegate);

            // act
            assertThrows(RuntimeException.class, () -> processor.processMessage(message, mockMessageResolver));

            // assert
            verify(delegate, never()).processMessage(any(), any());
        }

        @Test
        void preProcessorAreCalledWithMessageDetailsAndMessage() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QueueProperties.builder().build(),
                    ImmutableList.of(decorator), delegate);
            when(delegate.processMessage(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

            // act
            processor.processMessage(message, mockMessageResolver);

            // assert
            verify(decorator).onPreSupply(emptyContext, message);
        }

        @Test
        void onSuccessWillCallOnSupplySuccessAndFinished() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QueueProperties.builder().build(),
                    ImmutableList.of(decorator), delegate);
            when(delegate.processMessage(any(), any())).thenReturn(new CompletableFuture<>());

            // act
            processor.processMessage(message, mockMessageResolver);

            // assert
            verify(decorator).onSupplySuccess(emptyContext, message);
            verify(decorator, never()).onSupplyFailure(eq(emptyContext), eq(message), any());
            verify(decorator).onSupplyFinished(emptyContext, message);
            InOrder inOrder = inOrder(decorator, delegate);
            inOrder.verify(delegate).processMessage(any(), any());
            inOrder.verify(decorator).onSupplySuccess(any(), any());
            inOrder.verify(decorator).onSupplyFinished(any(), any());
        }

        @Test
        void willSupplyFailureWillCallOnSupplyFailureAndFinished() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QueueProperties.builder().build()
                    , ImmutableList.of(decorator), delegate);
            when(delegate.processMessage(any(), any())).thenThrow(ExpectedTestException.class);

            // act
            final ExpectedTestException exception = assertThrows(ExpectedTestException.class, () -> processor.processMessage(message, mockMessageResolver));

            // assert
            verify(decorator, never()).onSupplySuccess(emptyContext, message);
            verify(decorator).onSupplyFailure(emptyContext, message, exception);
            verify(decorator).onSupplyFinished(emptyContext, message);
            InOrder inOrder = inOrder(decorator, delegate);
            inOrder.verify(delegate).processMessage(any(), any());
            inOrder.verify(decorator).onSupplyFailure(emptyContext, message, exception);
            inOrder.verify(decorator).onSupplyFinished(emptyContext, message);
        }

        @Test
        void anyFailingOnSupplySuccessDecoratorWillNotStopOtherDecoratorsRunning() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            doThrow(ExpectedTestException.class).when(decorator).onSupplySuccess(any(), any());
            final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator, otherDecorator), delegate);
            doReturn(CompletableFuture.completedFuture("value")).when(delegate).processMessage(any(), any());

            // act
            processor.processMessage(message, mockMessageResolver);

            // assert
            verify(otherDecorator).onSupplySuccess(any(), any());
        }

        @Test
        void anyFailingOnSupplyFailureDecoratorWillNotStopOtherDecoratorsRunning() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            doThrow(ExpectedTestException.class).when(decorator).onSupplyFailure(any(), any(), any());
            final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator, otherDecorator), delegate);
            when(delegate.processMessage(any(), any())).thenThrow(ExpectedTestException.class);

            // act
            assertThrows(ExpectedTestException.class, () -> processor.processMessage(message, mockMessageResolver));

            // assert
            verify(otherDecorator).onSupplyFailure(any(), any(), any());
        }

        @Test
        void anyFailingOnSupplyFinishedDecoratorWillNotStopOtherDecoratorsRunning() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            doThrow(ExpectedTestException.class).when(decorator).onSupplyFinished(any(), any());
            final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator, otherDecorator), delegate);
            when(delegate.processMessage(any(), any())).thenReturn(new CompletableFuture<>());

            // act
            processor.processMessage(message, mockMessageResolver);

            // assert
            verify(otherDecorator).onSupplyFinished(any(), any());
        }
    }

    @Nested
    class MessageProcessing {
        @Test
        void willCallMessageProcessingFunctionsOnMessageProcessingSuccess() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator), delegate);
            final CompletableFuture<String> future = new CompletableFuture<>();
            doReturn(future).when(delegate).processMessage(any(), any());
            processor.processMessage(message, mockMessageResolver);

            // act
            verify(decorator, never()).onMessageProcessingSuccess(any(), any(), any());
            future.complete("value");

            // assert
            verify(decorator).onMessageProcessingSuccess(emptyContext, message, "value");
            verify(decorator, never()).onMessageProcessingFailure(any(), any(), any());
            verify(decorator).onMessageProcessingFinished(emptyContext, message);
        }

        @Test
        void willCallMessageProcessingFunctionsOnMessageProcessingFailure() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator), delegate);
            final CompletableFuture<String> future = new CompletableFuture<>();
            doReturn(future).when(delegate).processMessage(any(), any());
            processor.processMessage(message, mockMessageResolver);

            // act
            verify(decorator, never()).onMessageProcessingSuccess(any(), any(), any());
            final ExpectedTestException exception = new ExpectedTestException();
            future.completeExceptionally(exception);

            // assert
            verify(decorator, never()).onMessageProcessingSuccess(any(), any(), any());
            verify(decorator).onMessageProcessingFailure(emptyContext, message, exception);
            verify(decorator).onMessageProcessingFinished(emptyContext, message);
        }

        @Test
        void anyFailingOnMessageProcessingSuccessDecoratorWillNotStopOtherDecoratorsFromRunning() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            doThrow(ExpectedTestException.class).when(decorator).onMessageProcessingSuccess(any(), any(), any());
            final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator, otherDecorator), delegate);
            doReturn(CompletableFuture.completedFuture("value")).when(delegate).processMessage(any(), any());

            // act
            processor.processMessage(message, mockMessageResolver);

            // assert
            verify(otherDecorator).onMessageProcessingSuccess(any(), any(), any());
        }

        @Test
        void anyFailingOnMessageProcessingFailureDecoratorWillNotStopOtherDecoratorsFromRunning() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            doThrow(ExpectedTestException.class).when(decorator).onMessageProcessingFailure(any(), any(), any());
            final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator, otherDecorator), delegate);
            doReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException())).when(delegate).processMessage(any(), any());

            // act
            processor.processMessage(message, mockMessageResolver);

            // assert
            verify(otherDecorator).onMessageProcessingFailure(any(), any(), any());
        }

        @Test
        void anyFailingOnMessageProcessingFinishedDecoratorWillNotStopOtherDecoratorsFromRunning() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            doThrow(ExpectedTestException.class).when(decorator).onMessageProcessingFinished(any(), any());
            final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator, otherDecorator), delegate);
            doReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException())).when(delegate).processMessage(any(), any());

            // act
            processor.processMessage(message, mockMessageResolver);

            // assert
            verify(otherDecorator).onMessageProcessingFinished(any(), any());
        }
    }

    @Nested
    class MessageResolve {

        @Captor
        ArgumentCaptor<Supplier<CompletableFuture<?>>> resolverSupplierCaptor;

        @Test
        void willCallOnMessageResolveDecoratorsOnMessageResolveSuccess() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator), delegate);
            doReturn(CompletableFuture.completedFuture(null)).when(delegate).processMessage(any(), any());
            doReturn(CompletableFuture.completedFuture(null)).when(mockMessageResolver).get();
            processor.processMessage(message, mockMessageResolver);

            // act
            verify(decorator, never()).onMessageResolvedSuccess(any(), any());
            verify(delegate).processMessage(eq(message), resolverSupplierCaptor.capture());
            resolverSupplierCaptor.getValue().get(); // trigger resolved

            // assert
            verify(decorator).onMessageResolvedSuccess(emptyContext, message);
            verify(decorator, never()).onMessageResolvedFailure(any(), any(), any());
        }

        @Test
        void willCallOnMessageResolveDecoratorsOnMessageResolveFailure() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator), delegate);
            doReturn(CompletableFuture.completedFuture(null)).when(delegate).processMessage(any(), any());
            final ExpectedTestException exception = new ExpectedTestException();
            doReturn(CompletableFutureUtils.completedExceptionally(exception)).when(mockMessageResolver).get();
            processor.processMessage(message, mockMessageResolver);

            // act
            verify(decorator, never()).onMessageResolvedSuccess(any(), any());
            verify(delegate).processMessage(eq(message), resolverSupplierCaptor.capture());
            resolverSupplierCaptor.getValue().get(); // trigger resolved

            // assert
            verify(decorator, never()).onMessageResolvedSuccess(emptyContext, message);
            verify(decorator).onMessageResolvedFailure(emptyContext, message, exception);
        }

        @Test
        void anyFailingOnMessageResolveSuccessDecoratorWillNotStopOtherDecoratorsFromRunning() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            doThrow(ExpectedTestException.class).when(decorator).onMessageResolvedSuccess(any(), any());
            final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator, otherDecorator), delegate);
            doReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException())).when(delegate).processMessage(any(), any());
            doReturn(CompletableFuture.completedFuture(null)).when(mockMessageResolver).get();
            processor.processMessage(message, mockMessageResolver);

            // act
            verify(decorator, never()).onMessageResolvedSuccess(any(), any());
            verify(delegate).processMessage(eq(message), resolverSupplierCaptor.capture());
            resolverSupplierCaptor.getValue().get(); // trigger resolved

            // assert
            verify(otherDecorator).onMessageResolvedSuccess(any(), any());
        }

        @Test
        void anyFailingOnMessageResolveFailureDecoratorWillNotStopOtherDecoratorsFromRunning() {
            // arrange
            final MessageProcessingDecorator decorator = mock(MessageProcessingDecorator.class);
            doThrow(ExpectedTestException.class).when(decorator).onMessageResolvedFailure(any(), any(), any());
            final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator, otherDecorator), delegate);
            doReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException())).when(delegate).processMessage(any(), any());
            final ExpectedTestException exception = new ExpectedTestException();
            doReturn(CompletableFutureUtils.completedExceptionally(exception)).when(mockMessageResolver).get();
            processor.processMessage(message, mockMessageResolver);

            // act
            verify(decorator, never()).onMessageResolvedSuccess(any(), any());
            verify(delegate).processMessage(eq(message), resolverSupplierCaptor.capture());
            resolverSupplierCaptor.getValue().get(); // trigger resolved

            // assert
            verify(otherDecorator).onMessageResolvedFailure(any(), any(), any());
        }
    }

    @Nested
    class Context {
        @Captor
        ArgumentCaptor<Supplier<CompletableFuture<?>>> resolverSupplierCaptor;

        @Test
        void contextIsSharedBetweenEachStageOfProcessing() {
            // arrange
            @ParametersAreNonnullByDefault
            final MessageProcessingDecorator decorator = new MessageProcessingDecorator() {
                @Override
                public void onPreSupply(MessageProcessingContext context, Message message) {
                    context.setAttribute("onPreSupply", "onPreSupplyValue");
                }

                @Override
                public void onSupplySuccess(MessageProcessingContext context, Message message) {
                    context.setAttribute("onSupplySuccess", "onSupplySuccessValue");

                }

                @Override
                public void onMessageProcessingSuccess(MessageProcessingContext context, Message message, Object object) {
                    context.setAttribute("onMessageProcessingSuccess", "onMessageProcessingSuccessValue");

                }

                @Override
                public void onMessageResolvedSuccess(MessageProcessingContext context, Message message) {
                    context.setAttribute("onMessageResolvedSuccess", "onMessageResolvedSuccessValue");
                }
            };
            final MessageProcessingDecorator mockDecorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", queueProperties,
                    ImmutableList.of(decorator, mockDecorator), delegate);
            doReturn(CompletableFuture.completedFuture(null)).when(delegate).processMessage(any(), any());
            doReturn(CompletableFuture.completedFuture(null)).when(mockMessageResolver).get();

            // act
            processor.processMessage(message, mockMessageResolver);
            verify(delegate).processMessage(eq(message), resolverSupplierCaptor.capture());
            resolverSupplierCaptor.getValue().get(); // trigger resolved

            // assert
            final ArgumentCaptor<MessageProcessingContext> contextArgumentCaptor = ArgumentCaptor.forClass(MessageProcessingContext.class);
            verify(mockDecorator).onMessageResolvedSuccess(contextArgumentCaptor.capture(), eq(message));
            assertThat(contextArgumentCaptor.getValue().getAttributes()).isEqualTo(ImmutableMap.of(
                    "onPreSupply", "onPreSupplyValue",
                    "onSupplySuccess", "onSupplySuccessValue",
                    "onMessageProcessingSuccess", "onMessageProcessingSuccessValue",
                    "onMessageResolvedSuccess", "onMessageResolvedSuccessValue"
            ));
        }
    }
}
