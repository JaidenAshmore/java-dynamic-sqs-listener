package com.jashmore.sqs.util.thread;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.experimental.UtilityClass;

import java.util.concurrent.ThreadFactory;

@UtilityClass
public class ThreadUtils {
    /**
     * Build a {@link ThreadFactory} with the provided thread name format applied to all threads.
     *
     * @param threadNameFormat the thread name format to use
     * @return the generated thread factory
     */
    public ThreadFactory threadFactory(final String threadNameFormat) {
        return new ThreadFactoryBuilder()
                .setNameFormat(threadNameFormat)
                .build();
    }
}
