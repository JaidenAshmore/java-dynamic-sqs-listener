package com.jashmore.sqs.examples;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SomeService {
    private static final Random RANDOM = new Random();

    public void someMethod() throws InterruptedException {
        final Subsegment subsegment = AWSXRay.beginSubsegment("someMethod");
        try {
            if (RANDOM.nextBoolean()) {
                log.info("Service doing some processing");
                Thread.sleep(500);
            } else {
                Thread.sleep(200);
                throw new RuntimeException("Failed to process");
            }
        } catch (RuntimeException exception) {
            subsegment.addException(exception);
            throw exception;
        } finally {
            AWSXRay.endSubsegment();
        }
    }
}
