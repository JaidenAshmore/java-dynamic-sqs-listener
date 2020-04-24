package com.jashmore.sqs.util;

import java.util.concurrent.Semaphore;

/**
 * Semaphore that is able to dynamically update the number of available permits.
 */
public class ResizableSemaphore extends Semaphore {
    private int maximumPermits;

    public ResizableSemaphore(final int permits) {
        super(permits);

        this.maximumPermits = permits;
    }

    /**
     * Change the maximum number of permits available.
     *
     * <p>The changing of permit size is not thread safe and therefore this method should only be used by a single thread. E.g. only one thread has the
     * responsibility of changing the permit size.
     *
     * @param permits new max size for permits
     */
    public void changePermitSize(final int permits) {
        if (permits > this.maximumPermits) {
            this.release(permits - this.maximumPermits);
        } else if (permits < this.maximumPermits) {
            this.reducePermits(this.maximumPermits - permits);
        }
        this.maximumPermits = permits;
    }

    public int getMaximumPermits() {
        return maximumPermits;
    }
}
