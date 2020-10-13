package com.jashmore.sqs.util.thread;

import com.jashmore.documentation.annotations.Nonnull;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ThreadUtils {

    /**
     * Build a {@link ThreadFactory} with the provided thread name format applied to all threads.
     *
     * <p>For example if the prefix is 'my-listener', the thread name will be in the format 'my-listener-0', 'my-listener-1', etc.
     *
     * @param threadNamePrefix the prefix for the thread name
     * @return the generated thread factory
     */
    public ThreadFactory multiNamedThreadFactory(final String threadNamePrefix) {
        return new NamedThreadFactory(threadCount -> threadNamePrefix + "-" + threadCount);
    }

    /**
     * Build a {@link ThreadFactory} that will have the same name for each thread created.
     *
     * @param threadName the name of the thread
     * @return the generated thread factory
     */
    public ThreadFactory singleNamedThreadFactory(final String threadName) {
        return new NamedThreadFactory(threadCount -> threadName);
    }

    private class NamedThreadFactory implements ThreadFactory {

        private final ThreadFactory delegate;
        private final AtomicLong threadCount = new AtomicLong(0);
        private Function<Long, String> nameGenerator;

        public NamedThreadFactory(final Function<Long, String> nameGenerator) {
            this.delegate = Executors.defaultThreadFactory();
            this.nameGenerator = nameGenerator;
        }

        @Override
        public Thread newThread(@Nonnull final Runnable runnable) {
            final Thread thread = delegate.newThread(runnable);
            thread.setName(nameGenerator.apply(threadCount.getAndIncrement()));
            return thread;
        }
    }
}
