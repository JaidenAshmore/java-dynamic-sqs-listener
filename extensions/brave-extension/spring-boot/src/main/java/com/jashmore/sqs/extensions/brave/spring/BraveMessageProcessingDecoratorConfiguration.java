package com.jashmore.sqs.extensions.brave.spring;

import brave.Tracing;
import com.jashmore.sqs.extensions.brave.decorator.BraveMessageProcessingDecorator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "brave.Tracing")
public class BraveMessageProcessingDecoratorConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public BraveMessageProcessingDecorator braveMessageProcessorDecorator(final Tracing tracing) {
        return new BraveMessageProcessingDecorator(tracing);
    }
}
