package com.jashmore.sqs.config;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueAnnotationProcessor;
import com.jashmore.sqs.QueueLifecycleHandler;
import com.jashmore.sqs.QueueListenerAnnotationProcessor;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DefaultArgumentResolverService;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.Executors;

@Configuration
public class DynamicSqsListenerSpringStarterConfiguration {
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public PayloadMapper payloadMapper(final ObjectMapper objectMapper) {
        return new JacksonPayloadMapper(objectMapper);
    }

    @Bean
    public ArgumentResolverService argumentResolverService(final PayloadMapper payloadMapper, final AmazonSQSAsync amazonSqsAsync) {
        return new DefaultArgumentResolverService(payloadMapper, amazonSqsAsync);
    }

    @Bean
    public QueueListenerAnnotationProcessor queueListenerAnnotationProcessor(
            final ArgumentResolverService argumentResolverService, final AmazonSQSAsync amazonSqsAsync) {
        return new QueueListenerAnnotationProcessor(argumentResolverService, amazonSqsAsync, Executors.newCachedThreadPool());
    }

    @Bean
    public QueueLifecycleHandler queueLifecycleHandler(final ApplicationContext applicationContext,
                                                       final BeanFactory beanFactory,
                                                       final List<QueueAnnotationProcessor> annotationProcessors) {
        return new QueueLifecycleHandler(applicationContext, beanFactory, annotationProcessors);
    }
}
