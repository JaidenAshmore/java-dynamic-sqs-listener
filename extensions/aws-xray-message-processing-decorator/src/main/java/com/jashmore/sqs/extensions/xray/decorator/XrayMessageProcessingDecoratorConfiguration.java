package com.jashmore.sqs.extensions.xray.decorator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class XrayMessageProcessingDecoratorConfiguration {

    @Bean
    public BasicXrayMessageProcessingDecorator xrayMessageDecorator() {
        return new BasicXrayMessageProcessingDecorator();
    }
}
