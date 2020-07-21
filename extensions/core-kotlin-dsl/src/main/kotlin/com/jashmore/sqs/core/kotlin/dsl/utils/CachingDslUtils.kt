package com.jashmore.sqs.core.kotlin.dsl.utils

import java.time.Duration

/**
 * Cached implementations that works when the usage of it is single threaded.
 *
 * If this cached function is used in multiple threads, it may result in multiple calls to get the current value at the same time.
 *
 * @param timeout  the amount of time that a value should be cached for
 * @param delegate the delegate method to get the value when the cache has expired
 * @param <T> the type of the value to cache
 */
fun <T> cached(timeout: Duration, delegate: () -> T): () -> T {
    var timeOfLastCachedValueInMs: Long = System.currentTimeMillis() - timeout.toMillis() - 1
    var cachedValue: T? = null

    return {
        if (timeOfLastCachedValueInMs + timeout.toMillis() < System.currentTimeMillis()) {
            cachedValue = delegate()
            timeOfLastCachedValueInMs = System.currentTimeMillis()
        }
        cachedValue!!
    }
}