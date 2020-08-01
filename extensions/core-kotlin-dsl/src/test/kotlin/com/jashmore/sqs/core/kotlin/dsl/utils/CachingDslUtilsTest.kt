package com.jashmore.sqs.core.kotlin.dsl.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

class CachingDslUtilsTest {

    @Nested
    inner class Cached {
        @Test
        fun `should call to delegate the first time the supplier is used`() {
            val cachedSupplier = cached(Duration.ofMillis(500)) { 5 }
            assertThat(cachedSupplier()).isEqualTo(5)
        }

        @Test
        fun `should keep same value while timeout has not expired`() {
            var count = 0
            val cachedSupplier = cached(Duration.ofMillis(500)) { count++ }
            assertThat(cachedSupplier()).isEqualTo(0)
            assertThat(cachedSupplier()).isEqualTo(0)
            assertThat(cachedSupplier()).isEqualTo(0)
        }

        @Test
        fun `should use new value when timeout has expired`() {
            var count = 0
            val cachedSupplier = cached(Duration.ofMillis(500)) { count++ }
            assertThat(cachedSupplier()).isEqualTo(0)

            Thread.sleep(501)

            assertThat(cachedSupplier()).isEqualTo(1)
            assertThat(cachedSupplier()).isEqualTo(1)
        }

        @Test
        fun `will continually get new values when timeouts keeps expiring`() {
            var count = 0
            val cachedSupplier = cached(Duration.ofMillis(200)) { count++ }
            assertThat(cachedSupplier()).isEqualTo(0)
            Thread.sleep(100)
            assertThat(cachedSupplier()).isEqualTo(0)
            Thread.sleep(200)
            assertThat(cachedSupplier()).isEqualTo(1)
            Thread.sleep(150)
            assertThat(cachedSupplier()).isEqualTo(1)
            Thread.sleep(100)
            assertThat(cachedSupplier()).isEqualTo(2)
        }
    }
}
