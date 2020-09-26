package com.jashmore.sqs.core.kotlin.dsl.broker

import com.jashmore.sqs.core.kotlin.dsl.utils.RequiredFieldException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.MESSAGE_GROUP_ID
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
import java.util.function.Supplier

class GroupingMessageBrokerDslBuilderTest {

    @Test
    fun `no concurrency level set will throw exception`() {
        // act
        val exception = assertThrows(RequiredFieldException::class.java) {
            groupingBroker {
                messageGroupingFunction = {
                    it.attributes()[MESSAGE_GROUP_ID] ?: throw RuntimeException("No messageGroupId found")
                }
            }()
        }

        // assert
        assertThat(exception).hasMessage("concurrencyLevel is required for GroupingMessageBroker")
    }

    @Test
    fun `no message grouping function will throw exception`() {
        // act
        val exception = assertThrows(RequiredFieldException::class.java) {
            groupingBroker {
                concurrencyLevel = { 1 }
            }()
        }

        // assert
        assertThat(exception).hasMessage("messageGroupingFunction is required for GroupingMessageBroker")
    }

    @Test
    fun `will allow processing of messages concurrently`() {
        // arrange
        val staticConcurrencyLevel = 5
        val latch = CountDownLatch(staticConcurrencyLevel)
        val groupId = AtomicInteger()

        // act
        val broker = groupingBroker {
            concurrencyLevel = { staticConcurrencyLevel }
            messageGroupingFunction = {
                it.attributes()[MESSAGE_GROUP_ID] ?: throw RuntimeException("No messageGroupId found")
            }
            concurrencyPollingRate = { Duration.ofMinutes(1) }
            errorBackoffTime = { Duration.ofMinutes(2) }
            maximumNumberOfCachedMessageGroups = { 5 }
        }()
        val executorService = Executors.newSingleThreadExecutor()
        try {
            CompletableFuture.runAsync(
                Runnable {
                    broker.processMessages(
                        Executors.newCachedThreadPool(),
                        Supplier {
                            CompletableFuture.completedFuture(
                                Message.builder()
                                    .attributes(mutableMapOf(MESSAGE_GROUP_ID to "${groupId.getAndIncrement()}"))
                                    .build()
                            )
                        },
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
