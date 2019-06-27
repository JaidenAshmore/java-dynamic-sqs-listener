package com.jashmore.sqs.broker.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

public class CachingConcurrentMessageBrokerPropertiesTest {
    @Test
    public void concurrencyLevelIsCachedWithinTimeout() {
        // arrange
        final ConcurrentMessageBrokerProperties delegate = mock(ConcurrentMessageBrokerProperties.class);
        when(delegate.getConcurrencyLevel())
                .thenReturn(1)
                .thenReturn(2);
        final CachingConcurrentMessageBrokerProperties cachingProperties = new CachingConcurrentMessageBrokerProperties(100, delegate);

        // act
        cachingProperties.getConcurrencyLevel();
        int concurrencyLevel = cachingProperties.getConcurrencyLevel();

        // assert
        assertThat(concurrencyLevel).isEqualTo(1);
        verify(delegate, times(1)).getConcurrencyLevel();
    }

    @Test
    public void concurrencyLevelCacheIsClearedAfterTimeoutTimeout() throws InterruptedException {
        // arrange
        final ConcurrentMessageBrokerProperties delegate = mock(ConcurrentMessageBrokerProperties.class);
        when(delegate.getConcurrencyLevel())
                .thenReturn(1)
                .thenReturn(2);
        final CachingConcurrentMessageBrokerProperties cachingProperties = new CachingConcurrentMessageBrokerProperties(100, delegate);

        // act
        cachingProperties.getConcurrencyLevel();
        Thread.sleep(200);
        int concurrencyLevel = cachingProperties.getConcurrencyLevel();

        // assert
        assertThat(concurrencyLevel).isEqualTo(2);
        verify(delegate, times(2)).getConcurrencyLevel();
    }

    @Test
    public void concurrencyLevelWillStillResetAtTimeoutEvenWhenFrequentAccesses() throws InterruptedException {
        // arrange
        final ConcurrentMessageBrokerProperties delegate = mock(ConcurrentMessageBrokerProperties.class);
        when(delegate.getConcurrencyLevel())
                .thenReturn(1)
                .thenReturn(2);
        final CachingConcurrentMessageBrokerProperties cachingProperties = new CachingConcurrentMessageBrokerProperties(100, delegate);

        // act
        cachingProperties.getConcurrencyLevel();
        Thread.sleep(60);
        cachingProperties.getConcurrencyLevel();
        Thread.sleep(60);
        int concurrencyLevel = cachingProperties.getConcurrencyLevel();

        // assert
        assertThat(concurrencyLevel).isEqualTo(2);
        verify(delegate, times(2)).getConcurrencyLevel();
    }

    @Test
    public void cachingPollingRateIsCached() throws InterruptedException {
        // arrange
        final ConcurrentMessageBrokerProperties delegate = mock(ConcurrentMessageBrokerProperties.class);
        when(delegate.getPreferredConcurrencyPollingRateInMilliseconds())
                .thenReturn(1L)
                .thenReturn(2L);
        final CachingConcurrentMessageBrokerProperties cachingProperties = new CachingConcurrentMessageBrokerProperties(100, delegate);

        // act
        cachingProperties.getPreferredConcurrencyPollingRateInMilliseconds();
        Thread.sleep(60);
        long pollingRate = cachingProperties.getPreferredConcurrencyPollingRateInMilliseconds();

        // assert
        assertThat(pollingRate).isEqualTo(1);
        verify(delegate, times(1)).getPreferredConcurrencyPollingRateInMilliseconds();
    }

    @Test
    public void cachingPollingRateExpiresAfterTimePeriod() throws InterruptedException {
        // arrange
        final ConcurrentMessageBrokerProperties delegate = mock(ConcurrentMessageBrokerProperties.class);
        when(delegate.getPreferredConcurrencyPollingRateInMilliseconds())
                .thenReturn(1L)
                .thenReturn(2L);
        final CachingConcurrentMessageBrokerProperties cachingProperties = new CachingConcurrentMessageBrokerProperties(100, delegate);

        // act
        cachingProperties.getPreferredConcurrencyPollingRateInMilliseconds();
        Thread.sleep(120);
        long pollingRate = cachingProperties.getPreferredConcurrencyPollingRateInMilliseconds();

        // assert
        assertThat(pollingRate).isEqualTo(2);
        verify(delegate, times(2)).getPreferredConcurrencyPollingRateInMilliseconds();
    }

    @Test
    public void shouldInterruptThreadsProcessingMessagesOnShutdownIsCached() throws InterruptedException {
        // arrange
        final ConcurrentMessageBrokerProperties delegate = mock(ConcurrentMessageBrokerProperties.class);
        when(delegate.shouldInterruptThreadsProcessingMessagesOnShutdown())
                .thenReturn(true)
                .thenReturn(false);
        final CachingConcurrentMessageBrokerProperties cachingProperties = new CachingConcurrentMessageBrokerProperties(100, delegate);

        // act
        cachingProperties.getPreferredConcurrencyPollingRateInMilliseconds();
        Thread.sleep(60);
        boolean shouldInterruptThreadsProcessingMessagesOnShutdown = cachingProperties.shouldInterruptThreadsProcessingMessagesOnShutdown();

        // assert
        assertThat(shouldInterruptThreadsProcessingMessagesOnShutdown).isTrue();
        verify(delegate, times(1)).shouldInterruptThreadsProcessingMessagesOnShutdown();
    }

    @Test
    public void shouldInterruptThreadsProcessingMessagesOnShutdownCacheExpiresAfterTimePeriod() throws InterruptedException {
        // arrange
        final ConcurrentMessageBrokerProperties delegate = mock(ConcurrentMessageBrokerProperties.class);
        when(delegate.shouldInterruptThreadsProcessingMessagesOnShutdown())
                .thenReturn(true)
                .thenReturn(false);
        final CachingConcurrentMessageBrokerProperties cachingProperties = new CachingConcurrentMessageBrokerProperties(100, delegate);

        // act
        cachingProperties.getPreferredConcurrencyPollingRateInMilliseconds();
        Thread.sleep(120);
        boolean shouldInterruptThreadsProcessingMessagesOnShutdown = cachingProperties.shouldInterruptThreadsProcessingMessagesOnShutdown();

        // assert
        assertThat(shouldInterruptThreadsProcessingMessagesOnShutdown).isTrue();
        verify(delegate, times(1)).shouldInterruptThreadsProcessingMessagesOnShutdown();
    }

    @Test
    public void threadNameFormatIsNotCachedAndTakenInConstruction() throws Exception {
        final ConcurrentMessageBrokerProperties delegate = mock(ConcurrentMessageBrokerProperties.class);
        when(delegate.getThreadNameFormat())
                .thenReturn("test")
                .thenReturn("should_not_be_used");
        final CachingConcurrentMessageBrokerProperties cachingProperties = new CachingConcurrentMessageBrokerProperties(100, delegate);

        // act
        final String firstThreadNameFormat = cachingProperties.getThreadNameFormat();
        Thread.sleep(120);
        final String secondThreadNameFormat = cachingProperties.getThreadNameFormat();

        // assert
        assertThat(firstThreadNameFormat).isEqualTo("test");
        assertThat(secondThreadNameFormat).isEqualTo("test");
        verify(delegate, times(1)).getThreadNameFormat();
    }
}

