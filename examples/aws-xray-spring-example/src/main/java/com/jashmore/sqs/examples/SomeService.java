package com.jashmore.sqs.examples;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

@Slf4j
@Component
@XRayEnabled
public class SomeService {
    private static final Random RANDOM = new Random();

    public void someMethod() throws InterruptedException {
        final int number = RANDOM.nextInt(10);
        log.info("Number chosen: {}", number);
        if (number <= 7) {
            log.info("Service doing some processing");
            Thread.sleep(500);
        } else {
            Thread.sleep(200);
            throw new RuntimeException("Failed to process");
        }
    }
}
