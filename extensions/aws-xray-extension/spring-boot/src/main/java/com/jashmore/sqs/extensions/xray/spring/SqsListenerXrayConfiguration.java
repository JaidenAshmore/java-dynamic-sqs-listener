package com.jashmore.sqs.extensions.xray.spring;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.jashmore.sqs.extensions.xray.client.StaticClientSegmentNamingStrategy;
import com.jashmore.sqs.extensions.xray.client.XrayWrappedSqsAsyncClient;
import com.jashmore.sqs.extensions.xray.decorator.BasicXrayMessageProcessingDecorator;
import com.jashmore.sqs.extensions.xray.decorator.StaticDecoratorSegmentNamingStrategy;
import com.jashmore.sqs.spring.client.DefaultSqsAsyncClientProvider;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Configuration
public class SqsListenerXrayConfiguration {
    @Bean
    @Qualifier("sqsXrayRecorder")
    @ConditionalOnMissingBean(name = "sqsXrayRecorder")
    public AWSXRayRecorder sqsXrayRecorder() {
        return AWSXRay.getGlobalRecorder();
    }

    @Bean
    public BasicXrayMessageProcessingDecorator xrayMessageDecorator(@Qualifier("sqsXrayRecorder") final AWSXRayRecorder recorder,
                                                                    @Value("${spring.application.name:service}") final String applicationName) {
        return new BasicXrayMessageProcessingDecorator(recorder, new StaticDecoratorSegmentNamingStrategy(applicationName));
    }

    @Configuration
    @ConditionalOnMissingBean(SqsAsyncClientProvider.class)
    public static class XraySqsAsyncClientProviderConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SqsAsyncClient sqsAsyncClient() {
            return SqsAsyncClient.create();
        }

        @Bean
        public SqsAsyncClientProvider xraySqsAsyncClientProvider(final SqsAsyncClient defaultClient,
                                                                 @Value("${spring.application.name:service}") final String applicationName,
                                                                 @Qualifier("sqsXrayRecorder") final AWSXRayRecorder recorder) {
            final XrayWrappedSqsAsyncClient xrayDefaultClient = new XrayWrappedSqsAsyncClient(
                    defaultClient,
                    recorder,
                    new StaticClientSegmentNamingStrategy(applicationName)
            );

            return new DefaultSqsAsyncClientProvider(xrayDefaultClient);
        }
    }
}
