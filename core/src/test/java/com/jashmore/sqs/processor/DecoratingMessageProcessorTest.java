package com.jashmore.sqs.processor;

import static com.jashmore.sqs.util.collections.CollectionUtils.immutableListOf;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.documentation.annotations.Nonnull;
import com.jashmore.documentation.annotations.Nullable;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import com.jashmore.sqs.util.ExpectedTestException;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@ExtendWith(MockitoExtension.class)
class DecoratingMessageProcessorTest {
    static QueueProperties QUEUE_PROPERTIES = QueueProperties.builder().build();

    MessageProcessingContext emptyContext = MessageProcessingContext.builder()
            .listenerIdentifier("identifier")
            .queueProperties(QUEUE_PROPERTIES)
            .attributes(new HashMap<>())
            .build();

    Message message = Message.builder().body("body").build();

    @Mock
    ArgumentResolverService argumentResolverService;

    @Mock
    SqsAsyncClient sqsAsyncClient;

    @Mock
    Supplier<CompletableFuture<?>> mockMessageResolver;

    @Mock
    private MessageProcessingDecorator decorator;

    @Nested
    class OnPreMessageProcessing {
        SynchronousMessageListenerScenarios synchronousMessageListener = new SynchronousMessageListenerScenarios();

        @Test
        void isCalledWithAnEmptyContext() {
            // when
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
            final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                    singletonList(decorator), delegate);
            when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

            // act
            processor.processMessage(message, mockMessageResolver);

            // assert
            verify(decorator).onPreMessageProcessing(eq(emptyContext), eq(message));
        }

        @Test
        void anyFailureWillNotRunSubsequentDecorators() {
            // when
            final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
            final MessageProcessingDecorator failingDecorator = mock(MessageProcessingDecorator.class);
            doThrow(ExpectedTestException.class).when(failingDecorator).onPreMessageProcessing(any(), any());
            final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                    immutableListOf(failingDecorator, otherDecorator), createCoreProcessor(synchronousMessageListener, method));

            // act
            final MessageProcessingException exception = assertThrows(MessageProcessingException.class,
                    () -> processor.processMessage(message, mockMessageResolver));

            // assert
            assertThat(exception).hasCauseInstanceOf(ExpectedTestException.class);
            verify(otherDecorator, never()).onPreMessageProcessing(any(), any());
        }

        @Test
        void anyFailureWillNotDelegateMessageProcessor() {
            // when
            final MessageProcessingDecorator failingDecorator = mock(MessageProcessingDecorator.class);
            doThrow(ExpectedTestException.class).when(failingDecorator).onPreMessageProcessing(any(), any());
            final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
            final MessageProcessor delegateProcessor = mock(MessageProcessor.class);
            final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                    Arrays.asList(failingDecorator, otherDecorator), delegateProcessor);

            // act
            assertThrows(MessageProcessingException.class,
                    () -> processor.processMessage(message, mockMessageResolver));

            // assert
            verify(delegateProcessor, never()).processMessage(any(), any());
        }
    }

    @Nested
    class Asynchronous {
        AsynchronousMessageListenerScenarios asynchronousMessageListenerScenarios = new AsynchronousMessageListenerScenarios();

        @Mock
        ArgumentResolver<CompletableFuture<?>> futureArgumentResolver;

        @Nested
        class OnMessageProcessingSuccess {

            @Test
            void isCalledOnMessageListenerSuccess() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodWithSuppliedFuture", CompletableFuture.class);
                doReturn(futureArgumentResolver).when(argumentResolverService).getArgumentResolver(any());
                final CompletableFuture<?> methodFuture = new CompletableFuture<>();
                doReturn(methodFuture).when(futureArgumentResolver).resolveArgumentForParameter(any(), any(), any());
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                final CompletableFuture<?> future = processor.processMessage(message, mockMessageResolver);
                assertThat(future).isNotDone();
                verify(decorator, never()).onMessageProcessingSuccess(any(), any(), any());
                methodFuture.complete(null);

                // assert
                verify(decorator).onMessageProcessingSuccess(eq(emptyContext), eq(message), isNull());
            }

            @Test
            void isNotCalledWhenMessageListenerThrowsException() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, asynchronousMessageListenerScenarios);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);

                // act
                final CompletableFuture<?> completableFuture = processor.processMessage(message, mockMessageResolver);

                // assert
                assertThat(completableFuture).isCompletedExceptionally();
                verify(decorator, never()).onMessageProcessingSuccess(any(), any(), any());
            }

            @Test
            @SneakyThrows
            void isNotCalledOnTheSameThreadAsMessageListener() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
                final AtomicReference<Thread> decoratorThread = new AtomicReference<>();
                final MessageProcessingDecorator decorator = new MessageProcessingDecorator() {
                    @Override
                    public void onMessageProcessingSuccess(MessageProcessingContext context, Message message, @Nullable Object object) {
                        decoratorThread.set(Thread.currentThread());
                    }
                };
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, asynchronousMessageListenerScenarios);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));
                final Thread currentThread = Thread.currentThread();

                // act
                processor.processMessage(message, mockMessageResolver).get(5, SECONDS);

                // assert
                assertThat(decoratorThread.get()).isNotEqualTo(currentThread);
            }

            @Test
            @SneakyThrows
            void willCallSubsequentDecoratorsIfOneFails() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(decorator, otherDecorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));
                doThrow(ExpectedTestException.class).when(decorator).onMessageProcessingSuccess(any(), any(), any());

                // act
                processor.processMessage(message, mockMessageResolver).get(5, SECONDS);

                // assert
                verify(otherDecorator).onMessageProcessingSuccess(any(), any(), any());
            }

            @Test
            @SneakyThrows
            void sameContextIsAppliedThroughDecorator() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final AtomicReference<MessageProcessingContext> contextReference = new AtomicReference<>();
                final MessageProcessingDecorator otherDecorator = new MessageProcessingDecorator() {
                    @Override
                    public void onPreMessageProcessing(MessageProcessingContext context, Message message) {
                        contextReference.set(context);
                    }
                };
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(otherDecorator, decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                processor.processMessage(message, mockMessageResolver).get(5, SECONDS);

                // assert
                verify(decorator).onMessageProcessingSuccess(eq(contextReference.get()), eq(message), isNull());
            }
        }

        @Nested
        class OnMessageProcessingFailure {

            @Test
            void isNotCalledOnMessageListenerSuccess() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodWithSuppliedFuture", CompletableFuture.class);
                doReturn(futureArgumentResolver).when(argumentResolverService).getArgumentResolver(any());
                final CompletableFuture<?> methodFuture = new CompletableFuture<>();
                doReturn(methodFuture).when(futureArgumentResolver).resolveArgumentForParameter(any(), any(), any());
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                final CompletableFuture<?> future = processor.processMessage(message, mockMessageResolver);
                assertThat(future).isNotDone();
                verify(decorator, never()).onMessageProcessingSuccess(any(), any(), any());
                methodFuture.complete(null);

                // assert
                verify(decorator, never()).onMessageProcessingFailure(eq(emptyContext), eq(message), any());
            }

            @Test
            void isCalledWhenMessageListenerThrowsException() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, asynchronousMessageListenerScenarios);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);

                // act
                final CompletableFuture<?> completableFuture = processor.processMessage(message, mockMessageResolver);

                // assert
                assertThat(completableFuture).isCompletedExceptionally();
                verify(decorator).onMessageProcessingFailure(eq(emptyContext), eq(message), any());
            }

            @Test
            @SneakyThrows
            void isNotCalledOnTheSameThreadAsMessageListener() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyRejected");
                final AtomicReference<Thread> decoratorThread = new AtomicReference<>();
                final MessageProcessingDecorator decorator = new MessageProcessingDecorator() {
                    @Override
                    public void onMessageProcessingFailure(MessageProcessingContext context, Message message, Throwable object) {
                        decoratorThread.set(Thread.currentThread());
                    }
                };
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, asynchronousMessageListenerScenarios);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                final Thread currentThread = Thread.currentThread();

                // act
                assertThrows(ExecutionException.class, () -> processor.processMessage(message, mockMessageResolver).get(5, SECONDS));

                // assert
                assertThat(decoratorThread).isNotNull();
                assertThat(decoratorThread.get()).isNotEqualTo(currentThread);
            }

            @Test
            @SneakyThrows
            void willCallSubsequentDecoratorsIfOneFails() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyRejected");
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(decorator, otherDecorator), delegate);
                doThrow(ExpectedTestException.class).when(decorator).onMessageProcessingFailure(any(), any(), any());

                // act
                assertThrows(ExecutionException.class, () -> processor.processMessage(message, mockMessageResolver).get(5, SECONDS));

                // assert
                verify(otherDecorator).onMessageProcessingFailure(any(), any(), any());
            }

            @Test
            @SneakyThrows
            void sameContextIsAppliedThroughDecorator() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyRejected");
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final AtomicReference<MessageProcessingContext> contextReference = new AtomicReference<>();
                final MessageProcessingDecorator otherDecorator = new MessageProcessingDecorator() {
                    @Override
                    public void onPreMessageProcessing(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                        contextReference.set(context);
                    }
                };
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(otherDecorator, decorator), delegate);

                // act
                assertThrows(ExecutionException.class, () -> processor.processMessage(message, mockMessageResolver).get(5, SECONDS));

                // assert
                verify(decorator).onMessageProcessingFailure(eq(contextReference.get()), eq(message), any());
            }
        }

        @Nested
        class OnMessageProcessingThreadComplete {

            @Test
            void isCalledOnMessageListenerSuccess() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodWithSuppliedFuture", CompletableFuture.class);
                doReturn(futureArgumentResolver).when(argumentResolverService).getArgumentResolver(any());
                final CompletableFuture<?> methodFuture = new CompletableFuture<>();
                doReturn(methodFuture).when(futureArgumentResolver).resolveArgumentForParameter(any(), any(), any());
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                final CompletableFuture<?> future = processor.processMessage(message, mockMessageResolver);
                assertThat(future).isNotDone();
                verify(decorator, never()).onMessageProcessingSuccess(any(), any(), any());
                methodFuture.complete(null);

                // assert
                verify(decorator).onMessageProcessingThreadComplete(eq(emptyContext), eq(message));
            }

            @Test
            void isCalledWhenMessageListenerThrowsException() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, asynchronousMessageListenerScenarios);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);

                // act
                final CompletableFuture<?> completableFuture = processor.processMessage(message, mockMessageResolver);

                // assert
                assertThat(completableFuture).isCompletedExceptionally();
                verify(decorator).onMessageProcessingThreadComplete(eq(emptyContext), eq(message));
            }

            @Test
            @SneakyThrows
            void isCalledOnTheSameThreadAsMessageListener() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
                final AtomicReference<Thread> decoratorThread = new AtomicReference<>();
                final MessageProcessingDecorator decorator = new MessageProcessingDecorator() {
                    @Override
                    public void onMessageProcessingThreadComplete(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                        decoratorThread.set(Thread.currentThread());
                    }
                };
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, asynchronousMessageListenerScenarios);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));
                final Thread currentThread = Thread.currentThread();

                // act
                processor.processMessage(message, mockMessageResolver).get(5, SECONDS);

                // assert
                assertThat(decoratorThread.get()).isEqualTo(currentThread);
            }

            @Test
            @SneakyThrows
            void willCallSubsequentDecoratorsIfOneFails() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(decorator, otherDecorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));
                doThrow(ExpectedTestException.class).when(decorator).onMessageProcessingThreadComplete(any(), any());

                // act
                processor.processMessage(message, mockMessageResolver).get(5, SECONDS);

                // assert
                verify(otherDecorator).onMessageProcessingThreadComplete(any(), any());
            }

            @Test
            @SneakyThrows
            void sameContextIsAppliedThroughDecorator() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final AtomicReference<MessageProcessingContext> contextReference = new AtomicReference<>();
                final MessageProcessingDecorator otherDecorator = new MessageProcessingDecorator() {
                    @Override
                    public void onPreMessageProcessing(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                        contextReference.set(context);
                    }
                };
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(otherDecorator, decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                processor.processMessage(message, mockMessageResolver).get(5, SECONDS);

                // assert
                verify(decorator).onMessageProcessingThreadComplete(eq(contextReference.get()), eq(message));
            }
        }

        @Nested
        class OnMessageResolvedSuccess {

            @Test
            void isCalledOnMessageListenerSuccessAndResolvingSuccess() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodWithSuppliedFuture", CompletableFuture.class);
                doReturn(futureArgumentResolver).when(argumentResolverService).getArgumentResolver(any());
                final CompletableFuture<?> methodFuture = new CompletableFuture<>();
                doReturn(methodFuture).when(futureArgumentResolver).resolveArgumentForParameter(any(), any(), any());
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                final CompletableFuture<?> future = processor.processMessage(message, mockMessageResolver);
                assertThat(future).isNotDone();
                verify(decorator, never()).onMessageProcessingSuccess(any(), any(), any());
                methodFuture.complete(null);

                // assert
                verify(decorator).onMessageResolvedSuccess(eq(emptyContext), eq(message));
            }

            @Test
            void isNotCalledWhenMessageListenerThrowsException() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, asynchronousMessageListenerScenarios);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);

                // act
                final CompletableFuture<?> completableFuture = processor.processMessage(message, mockMessageResolver);

                // assert
                assertThat(completableFuture).isCompletedExceptionally();
                verify(decorator, never()).onMessageResolvedSuccess(any(), any());
            }

            @Test
            void isNotCalledWhenMessageResolvingIsRejected() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyRejected");
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, asynchronousMessageListenerScenarios);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);

                // act
                assertThrows(ExecutionException.class, () -> processor.processMessage(message, mockMessageResolver).get(5, SECONDS));

                // assert
                verify(decorator, never()).onMessageResolvedSuccess(any(), any());
            }

            @Test
            @SneakyThrows
            void isNotCalledOnTheSameThreadAsMessageListener() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
                final AtomicReference<Thread> decoratorThread = new AtomicReference<>();
                final MessageProcessingDecorator decorator = new MessageProcessingDecorator() {
                    @Override
                    public void onMessageResolvedSuccess(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                        decoratorThread.set(Thread.currentThread());
                    }
                };
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, asynchronousMessageListenerScenarios);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenAnswer(invocation -> CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException interruptedException) {
                        // do nothing
                    }
                }));
                final Thread currentThread = Thread.currentThread();

                // act
                processor.processMessage(message, mockMessageResolver).get(5, SECONDS);

                // assert
                assertThat(decoratorThread.get()).isNotEqualTo(currentThread);
            }

            @Test
            @SneakyThrows
            void willCallSubsequentDecoratorsIfOneFails() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(decorator, otherDecorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));
                doThrow(ExpectedTestException.class).when(decorator).onMessageResolvedSuccess(any(), any());

                // act
                processor.processMessage(message, mockMessageResolver).get(5, SECONDS);

                // assert
                verify(otherDecorator).onMessageResolvedSuccess(any(), any());
            }

            @Test
            @SneakyThrows
            void sameContextIsAppliedThroughDecorator() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final AtomicReference<MessageProcessingContext> contextReference = new AtomicReference<>();
                final MessageProcessingDecorator otherDecorator = new MessageProcessingDecorator() {
                    @Override
                    public void onPreMessageProcessing(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                        contextReference.set(context);
                    }
                };
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(otherDecorator, decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                processor.processMessage(message, mockMessageResolver).get(5, SECONDS);

                // assert
                verify(decorator).onMessageResolvedSuccess(eq(contextReference.get()), eq(message));
            }
        }

        @Nested
        class OnMessageResolvedFailure {

            @Test
            void isCalledOnMessageListenerSuccessAndResolvingFailure() {
                // arrange
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodWithSuppliedFuture", CompletableFuture.class);
                doReturn(futureArgumentResolver).when(argumentResolverService).getArgumentResolver(any());
                final CompletableFuture<?> methodFuture = new CompletableFuture<>();
                doReturn(methodFuture).when(futureArgumentResolver).resolveArgumentForParameter(any(), any(), any());
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()));

                // act
                final CompletableFuture<?> future = processor.processMessage(message, mockMessageResolver);
                assertThat(future).isNotDone();
                verify(decorator, never()).onMessageProcessingSuccess(any(), any(), any());
                methodFuture.complete(null);

                // assert
                verify(decorator).onMessageResolvedFailure(eq(emptyContext), eq(message), any());
            }

            @Test
            @SneakyThrows
            void isNotCalledWhenMessageResolvingIsSuccessful() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturningResolvedFuture");
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, asynchronousMessageListenerScenarios);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                processor.processMessage(message, mockMessageResolver).get(5, SECONDS);

                // assert
                verify(decorator, never()).onMessageResolvedFailure(any(), any(), any());
            }

            @Test
            @SneakyThrows
            void isNotCalledOnTheSameThreadAsMessageListener() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
                final AtomicReference<Thread> decoratorThread = new AtomicReference<>();
                final MessageProcessingDecorator decorator = new MessageProcessingDecorator() {
                    @Override
                    public void onMessageResolvedFailure(@Nonnull MessageProcessingContext context, @Nonnull Message message, @Nonnull Throwable throwable) {
                        decoratorThread.set(Thread.currentThread());
                    }
                };
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, asynchronousMessageListenerScenarios);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenAnswer(invocation -> CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException interruptedException) {
                        // do nothing
                    }
                    throw new ExpectedTestException();
                }));
                final Thread currentThread = Thread.currentThread();

                // act
                processor.processMessage(message, mockMessageResolver).get(5, SECONDS);

                // assert
                assertThat(decoratorThread.get()).isNotEqualTo(currentThread);
            }

            @Test
            @SneakyThrows
            void willCallSubsequentDecoratorsIfOneFails() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(decorator, otherDecorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()));
                doThrow(ExpectedTestException.class).when(decorator).onMessageResolvedFailure(any(), any(), any());

                // act
                processor.processMessage(message, mockMessageResolver).get(5, SECONDS);

                // assert
                verify(otherDecorator).onMessageResolvedFailure(any(), any(), any());
            }

            @Test
            @SneakyThrows
            void sameContextIsAppliedThroughDecorator() {
                // when
                final Method method = AsynchronousMessageListenerScenarios.getMethod("methodReturnFutureSubsequentlyResolved");
                final MessageProcessor delegate = createCoreProcessor(asynchronousMessageListenerScenarios, method);
                final AtomicReference<MessageProcessingContext> contextReference = new AtomicReference<>();
                final MessageProcessingDecorator otherDecorator = new MessageProcessingDecorator() {
                    @Override
                    public void onPreMessageProcessing(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                        contextReference.set(context);
                    }
                };
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(otherDecorator, decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()));

                // act
                processor.processMessage(message, mockMessageResolver).get(5, SECONDS);

                // assert
                verify(decorator).onMessageResolvedFailure(eq(contextReference.get()), eq(message), any());
            }
        }
    }

    @Nested
    class Synchronous {
        SynchronousMessageListenerScenarios synchronousMessageListener = new SynchronousMessageListenerScenarios();

        @Nested
        class OnMessageProcessingSuccess {
            @Test
            void isCalledOnMessageListenerSuccess() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator).onMessageProcessingSuccess(eq(emptyContext), eq(message), isNull());
            }

            @Test
            void isNotCalledWhenMessageListenerThrowsException() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, synchronousMessageListener);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator, never()).onMessageProcessingSuccess(any(), any(), any());
            }

            @Test
            void isCalledOnTheSameThreadOnMessageListenerSuccess() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final AtomicReference<Thread> decoratorThread = new AtomicReference<>();
                final MessageProcessingDecorator decorator = new MessageProcessingDecorator() {
                    @Override
                    public void onMessageProcessingSuccess(MessageProcessingContext context, Message message, @Nullable Object object) {
                        decoratorThread.set(Thread.currentThread());
                    }
                };
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, synchronousMessageListener);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));
                final Thread currentThread = Thread.currentThread();

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                assertThat(decoratorThread.get()).isEqualTo(currentThread);
            }

            @Test
            void willCallSubsequentDecoratorsIfOneFails() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(decorator, otherDecorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));
                doThrow(ExpectedTestException.class).when(decorator).onMessageProcessingSuccess(any(), any(), any());

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(otherDecorator).onMessageProcessingSuccess(any(), any(), any());
            }

            @Test
            void sameContextIsAppliedThroughDecorator() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final AtomicReference<MessageProcessingContext> contextReference = new AtomicReference<>();
                final MessageProcessingDecorator otherDecorator = new MessageProcessingDecorator() {
                    @Override
                    public void onPreMessageProcessing(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                        contextReference.set(context);
                    }
                };
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(otherDecorator, decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator).onMessageProcessingSuccess(eq(contextReference.get()), eq(message), isNull());
            }
        }

        @Nested
        class OnMessageProcessingFailure {
            @Test
            void isNotCalledOnMessageListenerSuccess() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator, never()).onMessageProcessingFailure(any(), any(), any());
            }

            @Test
            void isCalledWhenMessageListenerThrowsException() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, synchronousMessageListener);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator).onMessageProcessingFailure(any(), any(), any());
            }

            @Test
            void isCalledOnTheSameThreadOnMessageListenerSuccess() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
                final AtomicReference<Thread> decoratorThread = new AtomicReference<>();
                final MessageProcessingDecorator decorator = new MessageProcessingDecorator() {
                    @Override
                    public void onMessageProcessingFailure(@Nonnull MessageProcessingContext context, @Nonnull Message message, @Nonnull Throwable object) {
                        decoratorThread.set(Thread.currentThread());
                    }
                };
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, synchronousMessageListener);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                final Thread currentThread = Thread.currentThread();

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                assertThat(decoratorThread.get()).isEqualTo(currentThread);
            }

            @Test
            void willCallSubsequentDecoratorsIfOneFails() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(decorator, otherDecorator), delegate);
                doThrow(ExpectedTestException.class).when(decorator).onMessageProcessingFailure(any(), any(), any());

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(otherDecorator).onMessageProcessingFailure(any(), any(), any());
            }

            @Test
            void sameContextIsAppliedThroughDecorator() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final AtomicReference<MessageProcessingContext> contextReference = new AtomicReference<>();
                final MessageProcessingDecorator otherDecorator = new MessageProcessingDecorator() {
                    @Override
                    public void onPreMessageProcessing(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                        contextReference.set(context);
                    }
                };
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(otherDecorator, decorator), delegate);

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator).onMessageProcessingFailure(eq(contextReference.get()), eq(message), any());
            }
        }

        @Nested
        class OnMessageProcessingThreadComplete {
            @Test
            void isCalledOnMessageListenerSuccess() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator).onMessageProcessingThreadComplete(eq(emptyContext), eq(message));
            }

            @Test
            void isNotCalledWhenMessageListenerThrowsException() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, synchronousMessageListener);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator).onMessageProcessingThreadComplete(eq(emptyContext), eq(message));
            }

            @Test
            void isCalledOnTheSameThreadOnMessageListenerSuccess() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final AtomicReference<Thread> decoratorThread = new AtomicReference<>();
                final MessageProcessingDecorator decorator = new MessageProcessingDecorator() {
                    @Override
                    public void onMessageProcessingThreadComplete(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                        decoratorThread.set(Thread.currentThread());
                    }
                };
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, synchronousMessageListener);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));
                final Thread currentThread = Thread.currentThread();

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                assertThat(decoratorThread.get()).isEqualTo(currentThread);
            }

            @Test
            void willCallSubsequentDecoratorsIfOneFails() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(decorator, otherDecorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));
                doThrow(ExpectedTestException.class).when(decorator).onMessageProcessingThreadComplete(any(), any());

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(otherDecorator).onMessageProcessingThreadComplete(any(), any());
            }

            @Test
            void sameContextIsAppliedThroughDecorator() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final AtomicReference<MessageProcessingContext> contextReference = new AtomicReference<>();
                final MessageProcessingDecorator otherDecorator = new MessageProcessingDecorator() {
                    @Override
                    public void onPreMessageProcessing(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                        contextReference.set(context);
                    }
                };
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(otherDecorator, decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator).onMessageProcessingThreadComplete(eq(contextReference.get()), eq(message));
            }
        }

        @Nested
        class OnMessageResolvedSuccess {
            @Test
            void isCalledOnMessageListenerSuccessAndResolveSuccess() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator).onMessageResolvedSuccess(eq(emptyContext), eq(message));
            }

            @Test
            void isNotCalledWhenMessageListenerThrowsException() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodThatThrowsException");
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, synchronousMessageListener);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator, never()).onMessageResolvedSuccess(eq(emptyContext), eq(message));
            }

            @Test
            @SneakyThrows
            void isNotCalledOnTheSameThreadOnMessageListenerSuccess() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final AtomicReference<Thread> decoratorThread = new AtomicReference<>();
                final CountDownLatch resolvingComplete = new CountDownLatch(1);
                final MessageProcessingDecorator decorator = new MessageProcessingDecorator() {
                    @Override
                    public void onMessageResolvedSuccess(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                        decoratorThread.set(Thread.currentThread());
                        resolvingComplete.countDown();
                    }
                };
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, synchronousMessageListener);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenAnswer(invocation -> CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException interruptedException) {
                        // do nothing
                    }
                }));
                final Thread currentThread = Thread.currentThread();

                // act
                processor.processMessage(message, mockMessageResolver);
                assertThat(resolvingComplete.await(1, TimeUnit.SECONDS)).isTrue();

                // assert
                assertThat(decoratorThread.get()).isNotNull();
                assertThat(decoratorThread.get()).isNotEqualTo(currentThread);
            }

            @Test
            void willCallSubsequentDecoratorsIfOneFails() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(decorator, otherDecorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));
                doThrow(ExpectedTestException.class).when(decorator).onMessageResolvedSuccess(any(), any());

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(otherDecorator).onMessageResolvedSuccess(any(), any());
            }

            @Test
            void sameContextIsAppliedThroughDecorator() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final AtomicReference<MessageProcessingContext> contextReference = new AtomicReference<>();
                final MessageProcessingDecorator otherDecorator = new MessageProcessingDecorator() {
                    @Override
                    public void onPreMessageProcessing(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                        contextReference.set(context);
                    }
                };
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(otherDecorator, decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFuture.completedFuture(null));

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator).onMessageResolvedSuccess(eq(contextReference.get()), eq(message));
            }
        }

        @Nested
        class OnMessageResolvedFailure {
            @Test
            void isCalledOnMessageListenerSuccessAndResolveFailure() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()));

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator).onMessageResolvedFailure(eq(emptyContext), eq(message), any());
            }

            @Test
            @SneakyThrows
            void isNotCalledOnTheSameThreadOnMessageListenerSuccess() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final AtomicReference<Thread> decoratorThread = new AtomicReference<>();
                final CountDownLatch resolvingComplete = new CountDownLatch(1);
                final MessageProcessingDecorator decorator = new MessageProcessingDecorator() {
                    @Override
                    public void onMessageResolvedFailure(@Nonnull MessageProcessingContext context, @Nonnull Message message, @Nonnull Throwable throwable) {
                        decoratorThread.set(Thread.currentThread());
                        resolvingComplete.countDown();
                    }
                };
                final MessageProcessor delegate = new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                        sqsAsyncClient, method, synchronousMessageListener);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        singletonList(decorator), delegate);
                when(mockMessageResolver.get()).thenAnswer(invocation -> CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException interruptedException) {
                        // do nothing
                    }
                    throw new ExpectedTestException();
                }));
                final Thread currentThread = Thread.currentThread();

                // act
                processor.processMessage(message, mockMessageResolver);
                assertThat(resolvingComplete.await(1, TimeUnit.SECONDS)).isTrue();

                // assert
                assertThat(decoratorThread.get()).isNotNull();
                assertThat(decoratorThread.get()).isNotEqualTo(currentThread);
            }

            @Test
            void willCallSubsequentDecoratorsIfOneFails() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final MessageProcessingDecorator otherDecorator = mock(MessageProcessingDecorator.class);
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(decorator, otherDecorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()));
                doThrow(ExpectedTestException.class).when(decorator).onMessageResolvedFailure(any(), any(), any());

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(otherDecorator).onMessageResolvedFailure(any(), any(), any());
            }

            @Test
            void sameContextIsAppliedThroughDecorator() {
                // when
                final Method method = SynchronousMessageListenerScenarios.getMethod("methodWithNoArguments");
                final MessageProcessor delegate = createCoreProcessor(synchronousMessageListener, method);
                final AtomicReference<MessageProcessingContext> contextReference = new AtomicReference<>();
                final MessageProcessingDecorator otherDecorator = new MessageProcessingDecorator() {
                    @Override
                    public void onPreMessageProcessing(@Nonnull MessageProcessingContext context, @Nonnull Message message) {
                        contextReference.set(context);
                    }
                };
                final DecoratingMessageProcessor processor = new DecoratingMessageProcessor("identifier", QUEUE_PROPERTIES,
                        Arrays.asList(otherDecorator, decorator), delegate);
                when(mockMessageResolver.get()).thenReturn(CompletableFutureUtils.completedExceptionally(new ExpectedTestException()));

                // act
                processor.processMessage(message, mockMessageResolver);

                // assert
                verify(decorator).onMessageResolvedFailure(eq(contextReference.get()), eq(message), any());
            }
        }
    }

    private CoreMessageProcessor createCoreProcessor(final Object bean, final Method method) {
        return new CoreMessageProcessor(argumentResolverService, QUEUE_PROPERTIES,
                sqsAsyncClient, method, bean);
    }
}
