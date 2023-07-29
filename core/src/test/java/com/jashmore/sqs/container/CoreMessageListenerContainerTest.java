package com.jashmore.sqs.container;

import static com.jashmore.sqs.util.collections.CollectionUtils.immutableListOf;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jashmore.sqs.broker.MessageBroker;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.resolver.MessageResolver;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.util.concurrent.CompletableFutureUtils;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.Message;

@Slf4j
@ExtendWith(MockitoExtension.class)
class CoreMessageListenerContainerTest {

    private static final CompletableFuture<Message> STUB_MESSAGE_BROKER_DONE = CompletableFutureUtils.completedExceptionally(
        new RuntimeException("Expected Messages Done")
    );

    private static final StaticCoreMessageListenerContainerProperties DEFAULT_PROPERTIES = StaticCoreMessageListenerContainerProperties
        .builder()
        .shouldInterruptThreadsProcessingMessagesOnShutdown(true)
        .shouldProcessAnyExtraRetrievedMessagesOnShutdown(false)
        .messageProcessingShutdownTimeout(Duration.ofSeconds(5))
        .messageResolverShutdownTimeout(Duration.ofSeconds(5))
        .messageRetrieverShutdownTimeout(Duration.ofSeconds(5))
        .messageBrokerShutdownTimeout(Duration.ofSeconds(5))
        .build();

    @Mock
    private MessageRetriever messageRetriever;

    @Mock
    private MessageBroker messageBroker;

    @Mock
    private MessageProcessor messageProcessor;

    @Mock
    private MessageResolver messageResolver;

    @Captor
    private ArgumentCaptor<Supplier<CompletableFuture<?>>> messageResolverArgumentCaptor;

    @Test
    void passedInIdentifierIsReturnedFromGetIdentifier() {
        // arrange
        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );

        // act
        final String actualIdentifier = container.getIdentifier();

        // assert
        assertThat(actualIdentifier).isEqualTo("id");
    }

    @Test
    void forEachAvailableMessageFromRetrieverTheMessageWillBeProcessedViaTheProcessor() {
        // arrange
        final Message message = Message.builder().body("first").build();
        final Message secondMessage = Message.builder().body("second").build();
        when(messageRetriever.retrieveMessage())
            .thenReturn(CompletableFuture.completedFuture(message))
            .thenReturn(CompletableFuture.completedFuture(secondMessage))
            .thenReturn(STUB_MESSAGE_BROKER_DONE);
        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );

        // act
        container.runContainer();

        // assert
        verify(messageProcessor).processMessage(eq(message), any());
        verify(messageProcessor).processMessage(eq(secondMessage), any());
    }

    @Test
    void forEachMessageOnTheMessageRetrieverTheMessageResolverIsUsedAsTheRunnableMethodForMessageCompletion() {
        // arrange
        final Message message = Message.builder().body("first").build();
        when(messageRetriever.retrieveMessage())
            .thenReturn(CompletableFuture.completedFuture(message))
            .thenReturn(STUB_MESSAGE_BROKER_DONE);
        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );
        container.runContainer();
        verify(messageProcessor).processMessage(eq(message), messageResolverArgumentCaptor.capture());

        // act
        messageResolverArgumentCaptor.getValue().get();

        // assert
        verify(messageResolver).resolveMessage(message);
    }

    @Test
    void whenMessageBrokerFinishesWithoutThrowingInterruptedExceptionTheContainerShutsDown() throws Exception {
        // arrange
        final CoreMessageListenerContainer container = buildContainer(
            "id",
            messageBroker,
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );

        // act
        container.runContainer();

        // assert
        verify(messageBroker).processMessages(any(), any(), any());
    }

    @Test
    void messageResolverWillBeRunOnBackgroundThread() {
        // arrange
        when(messageRetriever.retrieveMessage()).thenReturn(STUB_MESSAGE_BROKER_DONE);
        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );

        // act
        container.runContainer();

        // assert
        verify(messageResolver).run();
    }

    @Test
    void whenMessageRetrieverIsAsyncItWillBeStartedOnBackgroundThread() {
        // arrange
        when(messageRetriever.retrieveMessage()).thenReturn(STUB_MESSAGE_BROKER_DONE);
        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );

        // act
        container.runContainer();

        // assert
        verify(messageRetriever).run();
    }

    @Test
    void messageResolverBackgroundThreadNameCreatedFromIdentifier() {
        // arrange
        final AtomicReference<String> resolverThreadName = new AtomicReference<>();
        doAnswer(invocation -> {
                resolverThreadName.set(Thread.currentThread().getName());
                return null;
            })
            .when(messageResolver)
            .run();
        final CoreMessageListenerContainer container = buildContainer(
            "container-id",
            messageBroker,
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );

        // act
        container.runContainer();

        // assert
        assertThat(resolverThreadName).hasValue("container-id-message-resolver");
    }

    @Test
    void messageRetrieverBackgroundThreadNameCreatedFromIdentifier() {
        // arrange
        final AtomicReference<String> retrieverThreadName = new AtomicReference<>();
        when(messageRetriever.run())
            .thenAnswer(invocation -> {
                retrieverThreadName.set(Thread.currentThread().getName());
                return emptyMap();
            });
        final CoreMessageListenerContainer container = buildContainer(
            "container-id",
            messageBroker,
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );

        // act
        container.runContainer();

        // assert
        assertThat(retrieverThreadName).hasValue("container-id-message-retriever");
    }

    @Test
    void messageProcessingThreadNamesShouldBeMadeFromIdentifier() {
        // arrange
        final AtomicReference<String> retrieverThreadName = new AtomicReference<>();
        doAnswer(invocation -> {
                retrieverThreadName.set(Thread.currentThread().getName());
                log.info("Processing message");
                return null;
            })
            .when(messageProcessor)
            .processMessage(any(Message.class), any());
        when(messageRetriever.retrieveMessage())
            .thenReturn(CompletableFuture.completedFuture(Message.builder().build()))
            .thenReturn(STUB_MESSAGE_BROKER_DONE);
        final CoreMessageListenerContainer container = buildContainer(
            "container-id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );

        // act
        container.runContainer();

        // assert
        assertThat(retrieverThreadName.get()).matches("container-id-message-processing-\\d+");
    }

    @Test
    void anyExtraMessagesLeftoverByAsyncMessageRetrieverWillNotBeProcessedOnShutdownWhenPropertyIsFalse() {
        // arrange
        when(messageRetriever.retrieveMessage()).thenReturn(STUB_MESSAGE_BROKER_DONE);
        when(messageRetriever.run()).thenReturn(singletonList(Message.builder().build()));
        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );

        // act
        container.runContainer();

        // assert
        verify(messageProcessor, never()).processMessage(any(Message.class), any());
    }

    @Test
    void anyExtraMessagesLeftoverByAsyncMessageRetrieverWillBeProcessedOnShutdownWhenPropertyIsTrue() {
        // arrange
        when(messageRetriever.retrieveMessage()).thenReturn(STUB_MESSAGE_BROKER_DONE);
        final Message firstExtraMessage = Message.builder().body("first").build();
        final Message secondExtraMessage = Message.builder().body("second").build();
        when(messageRetriever.run()).thenReturn(immutableListOf(firstExtraMessage, secondExtraMessage));
        final StaticCoreMessageListenerContainerProperties properties = DEFAULT_PROPERTIES
            .toBuilder()
            .shouldProcessAnyExtraRetrievedMessagesOnShutdown(true)
            .build();
        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            properties
        );

        // act
        container.runContainer();

        // assert
        verify(messageProcessor).processMessage(eq(firstExtraMessage), any());
        verify(messageProcessor).processMessage(eq(secondExtraMessage), any());
    }

    @Test
    void allMessageListenerThreadsWillBeShutdownWhenContainerShutdown() throws InterruptedException {
        // arrange
        when(messageRetriever.retrieveMessage()).thenReturn(STUB_MESSAGE_BROKER_DONE);
        when(messageRetriever.run()).thenReturn(emptyList());
        final StaticCoreMessageListenerContainerProperties properties = DEFAULT_PROPERTIES
            .toBuilder()
            .shouldProcessAnyExtraRetrievedMessagesOnShutdown(true)
            .build();
        final CoreMessageListenerContainer container = buildContainer(
            "my-specific-container-id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            properties
        );

        // act
        container.runContainer();
        Thread.sleep(1000); // let's just wait a little bit just to be sure that the thread is gone

        // assert
        assertThat(Thread.getAllStackTraces().keySet()).noneMatch(thread -> thread.getName().startsWith("my-specific-container-id"));
    }

    @Test
    void willInterruptMessagesProcessingDuringShutdownWhenPropertySetToTrue() {
        // arrange
        final CountDownLatch messageProcessing = new CountDownLatch(1);
        final Message firstExtraMessage = Message.builder().body("first").build();
        when(messageRetriever.retrieveMessage())
            .thenReturn(CompletableFuture.completedFuture(firstExtraMessage))
            .thenAnswer(invocationOnMock -> {
                messageProcessing.await();
                return STUB_MESSAGE_BROKER_DONE;
            });
        final AtomicBoolean wasThreadInterrupted = new AtomicBoolean(false);
        doAnswer(invocation -> {
                try {
                    messageProcessing.countDown();
                    Thread.sleep(500);
                } catch (InterruptedException interruptedException) {
                    wasThreadInterrupted.set(true);
                }
                return null;
            })
            .when(messageProcessor)
            .processMessage(any(Message.class), any());
        final StaticCoreMessageListenerContainerProperties properties = DEFAULT_PROPERTIES
            .toBuilder()
            .shouldInterruptThreadsProcessingMessagesOnShutdown(true)
            .build();
        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            properties
        );

        // act
        container.runContainer();

        // assert
        assertThat(wasThreadInterrupted.get()).isTrue();
    }

    @Test
    void willNotInterruptMessagesProcessingDuringShutdownWhenPropertySetToFalse() {
        // arrange
        final CountDownLatch messageProcessing = new CountDownLatch(1);
        final Message firstExtraMessage = Message.builder().body("first").build();
        when(messageRetriever.retrieveMessage())
            .thenReturn(CompletableFuture.completedFuture(firstExtraMessage))
            .thenAnswer(invocationOnMock -> {
                messageProcessing.await();
                return STUB_MESSAGE_BROKER_DONE;
            });
        final AtomicBoolean wasThreadInterrupted = new AtomicBoolean(false);
        doAnswer(invocation -> {
                try {
                    messageProcessing.countDown();
                    Thread.sleep(500);
                } catch (InterruptedException interruptedException) {
                    wasThreadInterrupted.set(true);
                }
                return null;
            })
            .when(messageProcessor)
            .processMessage(any(Message.class), any());
        final StaticCoreMessageListenerContainerProperties properties = DEFAULT_PROPERTIES
            .toBuilder()
            .shouldInterruptThreadsProcessingMessagesOnShutdown(false)
            .build();
        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            properties
        );

        // act
        container.runContainer();

        // assert
        assertThat(wasThreadInterrupted).isFalse();
    }

    @Test
    void whenContainerIsBeingStoppedAnyAsyncMessageRetrieverThreadWillBeInterrupted() {
        // arrange
        final AtomicBoolean messageRetrieverInterrupted = new AtomicBoolean(false);
        when(messageRetriever.retrieveMessage()).thenReturn(STUB_MESSAGE_BROKER_DONE);
        when(messageRetriever.run())
            .thenAnswer(invocation -> {
                try {
                    Thread.sleep(5000);
                } catch (final InterruptedException interruptedException) {
                    messageRetrieverInterrupted.set(true);
                }
                return null;
            });

        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );

        // act
        container.runContainer();

        // assert
        assertThat(messageRetrieverInterrupted.get()).isTrue();
    }

    @Test
    void whenContainerIsBeingStoppedAnyAsyncMessageResolverThreadWillBeInterrupted() {
        // arrange
        final AtomicBoolean messageResolverInterrupted = new AtomicBoolean(false);
        when(messageRetriever.retrieveMessage()).thenReturn(STUB_MESSAGE_BROKER_DONE);
        doAnswer(invocation -> {
                try {
                    Thread.sleep(5000);
                } catch (final InterruptedException interruptedException) {
                    messageResolverInterrupted.set(true);
                }
                return null;
            })
            .when(messageResolver)
            .run();

        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );

        // act
        container.runContainer();

        // assert
        assertThat(messageResolverInterrupted.get()).isTrue();
    }

    @Test
    void whenMessageRetrieverExceedsShutdownLimitTheRestOfTheShutdownProcessIsTriggered() {
        // arrange
        when(messageRetriever.retrieveMessage()).thenReturn(STUB_MESSAGE_BROKER_DONE);
        when(messageRetriever.run())
            .thenAnswer(invocation -> {
                try {
                    Thread.sleep(2000);
                } catch (final InterruptedException interruptedException) {
                    Thread.sleep(2000);
                }
                return emptyMap();
            });
        final AtomicBoolean messageResolverInterrupted = new AtomicBoolean(false);
        doAnswer(invocation -> {
                try {
                    Thread.sleep(2000);
                } catch (final InterruptedException interruptedException) {
                    messageResolverInterrupted.set(true);
                }
                return null;
            })
            .when(messageResolver)
            .run();
        final CoreMessageListenerContainerProperties properties = DEFAULT_PROPERTIES
            .toBuilder()
            .messageRetrieverShutdownTimeout(Duration.ofSeconds(1))
            .build();
        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            properties
        );

        // act
        container.runContainer();

        // assert
        assertThat(messageResolverInterrupted.get()).isTrue();
    }

    @Test
    void startingContainerWillRunTheMainProcessInTheBackground() throws Exception {
        // arrange
        final CountDownLatch messageRetrievedLatched = new CountDownLatch(1);
        when(messageRetriever.retrieveMessage())
            .thenAnswer(invocation -> {
                messageRetrievedLatched.countDown();
                return STUB_MESSAGE_BROKER_DONE;
            });

        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );

        // act
        container.start();
        messageRetrievedLatched.await();

        // assert
        verify(messageRetriever).retrieveMessage();
    }

    @Test
    void startingContainerThatHasAlreadyStartedWillReturnSameCompletableFuture() throws Exception {
        // arrange
        final CountDownLatch messageRetrievedLatched = new CountDownLatch(1);
        when(messageRetriever.retrieveMessage())
            .thenAnswer(invocation -> {
                messageRetrievedLatched.countDown();
                return STUB_MESSAGE_BROKER_DONE;
            });

        final CoreMessageListenerContainer container = buildContainer(
            "id",
            new StubMessageBroker(),
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );

        // act
        final CompletableFuture<?> containerFinishedFuture = container.start();
        messageRetrievedLatched.await();
        final CompletableFuture<?> secondContainerStartedFuture = container.start();

        // assert
        assertThat(containerFinishedFuture).isSameAs(secondContainerStartedFuture);
    }

    @Test
    void stoppingContainerThatHasStartedWillShutDownContainer() throws Exception {
        // arrange
        final CountDownLatch messageBrokerStartedLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
                messageBrokerStartedLatch.countDown();
                Thread.sleep(5000);
                return null;
            })
            .when(messageBroker)
            .processMessages(any(), any(), any());
        final CoreMessageListenerContainer container = buildContainer(
            "anotherId",
            messageBroker,
            messageResolver,
            messageProcessor,
            messageRetriever,
            DEFAULT_PROPERTIES
        );
        final CompletableFuture<?> containerFinished = container.start();

        // act
        messageBrokerStartedLatch.await();
        container.stop();

        // assert
        containerFinished.get();
    }

    private static CoreMessageListenerContainer buildContainer(
        final String identifier,
        final MessageBroker messageBroker,
        final MessageResolver messageResolver,
        final MessageProcessor messageProcessor,
        final MessageRetriever messageRetriever,
        final CoreMessageListenerContainerProperties properties
    ) {
        return new CoreMessageListenerContainer(
            identifier,
            () -> messageBroker,
            () -> messageRetriever,
            () -> messageProcessor,
            () -> messageResolver,
            properties
        );
    }

    /**
     * Very simple implementations of the {@link MessageBroker} that allows for easier testing of the container by allowing the broker to end any time
     * a {@link CompletableFuture} is provided with an exception. Use the {@link #STUB_MESSAGE_BROKER_DONE} to trigger the end of processing messages.
     */
    private static class StubMessageBroker implements MessageBroker {

        @Override
        public void processMessages(
            final ExecutorService messageProcessingExecutorService,
            final BooleanSupplier keepProcessingMessages,
            final Supplier<CompletableFuture<Message>> messageSupplier,
            final Function<Message, CompletableFuture<?>> messageProcessor
        ) throws InterruptedException {
            while (keepProcessingMessages.getAsBoolean()) {
                final CompletableFuture<Message> messageFuture = messageSupplier.get();
                if (messageFuture.isCompletedExceptionally()) {
                    throw new InterruptedException();
                }
                try {
                    final Message message = messageFuture.get();
                    messageProcessingExecutorService.submit(() -> messageProcessor.apply(message));
                } catch (ExecutionException executionException) {
                    throw new RuntimeException(executionException);
                }
            }
        }
    }
}
