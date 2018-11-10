package com.jashmore.sqs.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class ResizableSemaphoreTest {
    @Test
    public void initialPermitsIsPassedIntSuper() {
        // act
        final ResizableSemaphore resizableSemaphore = new ResizableSemaphore(5);

        // assert
        assertThat(resizableSemaphore.availablePermits()).isEqualTo(5);
    }

    @Test
    public void changingPermitsAllowsToAcquireThreads() {
        // arrange
        final ResizableSemaphore resizableSemaphore = new ResizableSemaphore(0);
        assertThat(resizableSemaphore.tryAcquire()).isFalse();

        // act
        resizableSemaphore.changePermitSize(1);

        // assert
        assertThat(resizableSemaphore.tryAcquire()).isTrue();
    }

    @Test
    public void changingPermitsToLessThanAvailableStillAllowsThreadsToRunUntilCompletion() throws InterruptedException {
        // arrange
        final ResizableSemaphore resizableSemaphore = new ResizableSemaphore(1);
        resizableSemaphore.acquire();

        // act
        resizableSemaphore.changePermitSize(0);

        // assert
        assertThat(resizableSemaphore.availablePermits()).isEqualTo(-1);
        resizableSemaphore.release();
        assertThat(resizableSemaphore.availablePermits()).isEqualTo(0);
    }

    @Test
    public void changingPermitSizeToSameAmountWhilstSomeAcquiredDoesNothing() throws InterruptedException {
        // arrange
        final ResizableSemaphore resizableSemaphore = new ResizableSemaphore(1);
        resizableSemaphore.acquire();

        // act
        resizableSemaphore.changePermitSize(1);

        // assert
        assertThat(resizableSemaphore.availablePermits()).isEqualTo(0);
        resizableSemaphore.release();
        assertThat(resizableSemaphore.availablePermits()).isEqualTo(1);
    }
}
