package com.jashmore.sqs.util.collections;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import lombok.experimental.UtilityClass;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;

@UtilityClass
public class QueueUtils {

    /**
     * Try to obtain the desired number of elements from the {@link BlockingQueue} over the given period of time.
     *
     * <p>If there was not that many elements obtained in the given time period, return as many as currently available.
     *
     * @param queue             the queue to drain from
     * @param drainToCollection the collection to drain the elements into
     * @param desiredElements   the number of elements to try and obtain
     * @param durationToWait    the amount of time that an be used to drain
     * @param <T>               the type of the queue elements
     * @throws InterruptedException if the the thread was interrupted while waiting for elements
     */
    public <T> void drain(final BlockingQueue<T> queue,
                          final Collection<T> drainToCollection,
                          final int desiredElements,
                          final Duration durationToWait) throws InterruptedException {
        final long endTimeInMs = System.currentTimeMillis() + durationToWait.toMillis();

        int elementsLeft = desiredElements;
        while (elementsLeft > 0) {
            final int actualElementsDrained = queue.drainTo(drainToCollection, elementsLeft);
            // We have drained all that we need so we can return
            if (actualElementsDrained == elementsLeft) {
                return;
            }
            elementsLeft -= actualElementsDrained;

            final T element = queue.poll(endTimeInMs - System.currentTimeMillis(), MILLISECONDS);
            // No element was found in the time limit so we return the number that we did actually collect
            if (element == null) {
                return;
            }

            drainToCollection.add(element);
            --elementsLeft;
        }
    }
}
