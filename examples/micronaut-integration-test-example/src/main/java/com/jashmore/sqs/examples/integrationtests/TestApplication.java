package com.jashmore.sqs.examples.integrationtests;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.micronaut.container.basic.QueueListener;
import io.micronaut.runtime.Micronaut;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;

public class TestApplication {

    public static void main(String[] args) {
        Micronaut.build(args)
                .mainClass(TestApplication.class)
                .start();
    }

    @Singleton
    public static class SomeService {

        /**
         * Process a message payload, no-op.
         *
         * @param payload the payload of the message
         */
        public void run(final String payload) {
            // do nothing
        }
    }

    @Singleton
    @AllArgsConstructor
    public static class MessageListener {

        private final SomeService someService;

        /**
         * We specifically override the visibility timeout here from the default of 30 to decrease the time
         * for the tests to run.
         *
         * @param message the payload of the message
         */

        @QueueListener(value = "${sqs.queues.integrationTestingQueue}", messageVisibilityTimeoutInSeconds = 2)
        public void messageListener(@Payload final String message) {
            someService.run(message);
        }
    }
}
