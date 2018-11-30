package com.jashmore.sqs.util;

import lombok.Getter;

import java.util.concurrent.Semaphore;

/**
 * Semaphore that is able to dynamically update the number of available permits.
 */
@Getter
public class ResizableSemaphore extends Semaphore {
    private int maximumPermits;

    public ResizableSemaphore(final int permits) {
        super(permits);

        this.maximumPermits = permits;
    }

    /**
     * Change the maximum number of permits available.
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
}
