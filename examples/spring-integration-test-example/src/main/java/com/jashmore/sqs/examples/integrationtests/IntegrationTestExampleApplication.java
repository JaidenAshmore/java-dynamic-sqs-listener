package com.jashmore.sqs.examples.integrationtests;

import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.annotations.core.basic.QueueListener;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Slf4j
@SuppressWarnings("unused")
@SpringBootApplication
public class IntegrationTestExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationTestExampleApplication.class);
    }

    @Service
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

    @Component
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
