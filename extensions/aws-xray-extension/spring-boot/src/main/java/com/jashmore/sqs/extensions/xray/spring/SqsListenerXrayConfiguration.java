package com.jashmore.sqs.extensions.xray.spring;

import com.amazonaws.xray.AWSXRay;
import com.jashmore.sqs.extensions.xray.client.StaticClientSegmentNamingStrategy;
import com.jashmore.sqs.extensions.xray.client.XrayWrappedSqsAsyncClient;
import com.jashmore.sqs.extensions.xray.decorator.BasicXrayMessageProcessingDecorator;
import com.jashmore.sqs.extensions.xray.decorator.StaticDecoratorSegmentNamingStrategy;
import com.jashmore.sqs.spring.client.DefaultSqsAsyncClientProvider;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Configuration
public class SqsListenerXrayConfiguration {

    @Bean
    public BasicXrayMessageProcessingDecorator xrayMessageDecorator(@Value("${spring.application.name:service}") final String applicationName) {
        return new BasicXrayMessageProcessingDecorator(new StaticDecoratorSegmentNamingStrategy(applicationName));
    }

    @Bean
    @ConditionalOnMissingBean
    public SqsAsyncClientProvider xraySqsAsyncClientProvider(final SqsAsyncClient defaultClient,
                                                             @Value("${spring.application.name:service}") final String applicationName) {
        final XrayWrappedSqsAsyncClient xrayDefaultClient = new XrayWrappedSqsAsyncClient(defaultClient, AWSXRay.getGlobalRecorder(),
                new StaticClientSegmentNamingStrategy(applicationName));

        return new DefaultSqsAsyncClientProvider(xrayDefaultClient);
    }
}
