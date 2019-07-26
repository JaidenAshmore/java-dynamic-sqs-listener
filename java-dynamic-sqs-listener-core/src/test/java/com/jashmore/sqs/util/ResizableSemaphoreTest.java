package com.jashmore.sqs.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import org.junit.jupiter.api.Test;

class ResizableSemaphoreTest {
    @Test
    void initialPermitsIsPassedIntSuper() {
        // act
        final ResizableSemaphore resizableSemaphore = new ResizableSemaphore(5);

        // assert
        assertThat(resizableSemaphore.availablePermits()).isEqualTo(5);
    }

    @Test
    void changingPermitsAllowsToAcquireThreads() {
        // arrange
        final ResizableSemaphore resizableSemaphore = new ResizableSemaphore(0);
        assertThat(resizableSemaphore.tryAcquire()).isFalse();

        // act
        resizableSemaphore.changePermitSize(1);

        // assert
        assertThat(resizableSemaphore.tryAcquire()).isTrue();
    }

    @Test
    void changingPermitsToLessThanAvailableStillAllowsThreadsToRunUntilCompletion() throws InterruptedException {
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
    void changingPermitSizeToSameAmountWhilstSomeAcquiredDoesNothing() throws InterruptedException {
        // arrange
        final ResizableSemaphore resizableSemaphore = new ResizableSemaphore(1) {
            @Override
            public void release(final int permits) {
                fail("Should not have released 0 permits");
            }

            @Override
            protected void reducePermits(final int reduction) {
                fail("Should not have reduced permits by zero");
            }
        };
        resizableSemaphore.acquire();

        // act
        resizableSemaphore.changePermitSize(1);

        // assert
        assertThat(resizableSemaphore.availablePermits()).isEqualTo(0);
        resizableSemaphore.release();
        assertThat(resizableSemaphore.availablePermits()).isEqualTo(1);
    }

    @Test
    void increasingPermitSizeShouldReleaseTheNumberOfExtraPermits() throws InterruptedException {
        // arrange
        final ResizableSemaphore resizableSemaphore = new ResizableSemaphore(1) {
            @Override
            public void release(final int permits) {
                assertThat(permits).isEqualTo(5);
                super.release(permits);
            }

            @Override
            protected void reducePermits(final int reduction) {
                fail("Should not have reduced permits");
            }
        };

        // act
        resizableSemaphore.changePermitSize(6);

        // assert
        assertThat(resizableSemaphore.availablePermits()).isEqualTo(6);
    }

    @Test
    void decreasePermitSizeShouldReduceTheNumberOfExtraPermits() throws InterruptedException {
        // arrange
        final ResizableSemaphore resizableSemaphore = new ResizableSemaphore(6) {
            @Override
            public void release(final int permits) {
                fail("Should not have released permits");
            }

            @Override
            protected void reducePermits(final int reduction) {
                assertThat(reduction).isEqualTo(5);
                super.reducePermits(reduction);
            }
        };

        // act
        resizableSemaphore.changePermitSize(1);

        // assert
        assertThat(resizableSemaphore.availablePermits()).isEqualTo(1);
    }
}
