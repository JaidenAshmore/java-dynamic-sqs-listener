package com.jashmore.sqs.broker.concurrent.properties;

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
                .thenReturn(1)
                .thenReturn(2);
        final CachingConcurrentMessageBrokerProperties cachingProperties = new CachingConcurrentMessageBrokerProperties(100, delegate);

        // act
        cachingProperties.getPreferredConcurrencyPollingRateInMilliseconds();
        Thread.sleep(60);
        int pollingRate = cachingProperties.getPreferredConcurrencyPollingRateInMilliseconds();

        // assert
        assertThat(pollingRate).isEqualTo(1);
        verify(delegate, times(1)).getPreferredConcurrencyPollingRateInMilliseconds();
    }


    @Test
    public void cachingPollingRateExpiresAfterTimePeriod() throws InterruptedException {
        // arrange
        final ConcurrentMessageBrokerProperties delegate = mock(ConcurrentMessageBrokerProperties.class);
        when(delegate.getPreferredConcurrencyPollingRateInMilliseconds())
                .thenReturn(1)
                .thenReturn(2);
        final CachingConcurrentMessageBrokerProperties cachingProperties = new CachingConcurrentMessageBrokerProperties(100, delegate);

        // act
        cachingProperties.getPreferredConcurrencyPollingRateInMilliseconds();
        Thread.sleep(120);
        int pollingRate = cachingProperties.getPreferredConcurrencyPollingRateInMilliseconds();

        // assert
        assertThat(pollingRate).isEqualTo(2);
        verify(delegate, times(2)).getPreferredConcurrencyPollingRateInMilliseconds();
    }
}

