package com.jashmore.sqs.core.kotlin.dsl.decorators

import com.jashmore.sqs.decorator.MessageProcessingDecorator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class CoreMessageProcessingDecoratorsDslBuilderTest {

    @Test
    fun `should be able to construct all decorators from provided ones`() {
        // arrange
        val decoratorOne = mock(MessageProcessingDecorator::class.java)
        val decoratorTwo = mock(MessageProcessingDecorator::class.java)
        val builder = CoreMessageProcessingDecoratorsDslDslBuilder()

        // act
        builder.add(decoratorOne)
        builder.add(decoratorTwo)

        // assert
        assertThat(builder()).containsExactly(decoratorOne, decoratorTwo)
    }
}
