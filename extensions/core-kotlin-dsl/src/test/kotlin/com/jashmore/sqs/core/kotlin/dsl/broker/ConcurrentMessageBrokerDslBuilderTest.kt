package com.jashmore.sqs.core.kotlin.dsl.broker

import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.model.Message
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Supplier

class ConcurrentMessageBrokerDslBuilderTest {

    @Test
    fun `no currency level supplier set will throw exception`() {
        // act
        val exception = assertThrows(RequiredFieldException::class.java) {
            concurrentBroker {
            }()
        }

        // assert
        assertThat(exception).hasMessage("concurrencyLevel is required for ConcurrentMessageBroker")
    }

    @Test
    fun `will allow processing of messages concurrently`() {
        // arrange
        val staticConcurrencyLevel = 5
        val latch = CountDownLatch(staticConcurrencyLevel)

        // act
        val broker = concurrentBroker {
            concurrencyLevel = { staticConcurrencyLevel }
        }()
        val executorService = Executors.newSingleThreadExecutor()
        try {
            CompletableFuture.runAsync(
                Runnable {
                    broker.processMessages(
                        Executors.newCachedThreadPool(),
                        Supplier { CompletableFuture.completedFuture(Message.builder().build()) },
                        processingMessageWillBlockUntilInterrupted(latch)
                    )
                },
                executorService
            )

            // assert
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        } finally {
            executorService.shutdownNow()
        }
    }

    private fun processingMessageWillBlockUntilInterrupted(messageProcessingLatch: CountDownLatch): Function<Message?, CompletableFuture<*>?>? {
        return processingMessageWillBlockUntilInterrupted(messageProcessingLatch, Runnable {})
    }

    private fun processingMessageWillBlockUntilInterrupted(
        messageProcessingLatch: CountDownLatch?,
        runnableCalledOnMessageProcessing: Runnable
    ): Function<Message?, CompletableFuture<*>?>? {
        return Function {
            CompletableFuture.runAsync(
                Runnable {
                    runnableCalledOnMessageProcessing.run()
                    messageProcessingLatch?.countDown()
                    try {
                        Thread.sleep(Long.MAX_VALUE)
                    } catch (interruptedException: InterruptedException) {
                        // expected
                    }
                },
                Executors.newCachedThreadPool()
            )
        }
    }
}
