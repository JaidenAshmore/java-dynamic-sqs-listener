package com.jashmore.sqs.extensions.xray.spring;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.jashmore.sqs.extensions.xray.client.ClientSegmentMutator;
import com.jashmore.sqs.extensions.xray.client.ClientSegmentNamingStrategy;
import com.jashmore.sqs.extensions.xray.client.StaticClientSegmentNamingStrategy;
import com.jashmore.sqs.extensions.xray.client.UnsampledClientSegmentMutator;
import com.jashmore.sqs.extensions.xray.client.XrayWrappedSqsAsyncClient;
import com.jashmore.sqs.extensions.xray.decorator.BasicXrayMessageProcessingDecorator;
import com.jashmore.sqs.extensions.xray.decorator.StaticDecoratorSegmentNamingStrategy;
import com.jashmore.sqs.spring.client.DefaultSqsAsyncClientProvider;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Configuration
@AutoConfigureBefore(QueueListenerConfiguration.class)
public class SqsListenerXrayConfiguration {

    @Bean
    @Qualifier("sqsXrayRecorder")
    @ConditionalOnMissingBean(name = "sqsXrayRecorder")
    public AWSXRayRecorder sqsXrayRecorder() {
        return AWSXRay.getGlobalRecorder();
    }

    @Bean
    @ConditionalOnMissingBean
    public BasicXrayMessageProcessingDecorator xrayMessageDecorator(
        @Qualifier("sqsXrayRecorder") final AWSXRayRecorder recorder,
        @Value("${spring.application.name:service}") final String applicationName
    ) {
        return new BasicXrayMessageProcessingDecorator(
            BasicXrayMessageProcessingDecorator
                .Options.builder()
                .recorder(recorder)
                .segmentNamingStrategy(new StaticDecoratorSegmentNamingStrategy(applicationName))
                .build()
        );
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
        @ConditionalOnMissingBean
        public ClientSegmentNamingStrategy clientSegmentNamingStrategy(
            @Value("${spring.application.name:service}") final String applicationName
        ) {
            return new StaticClientSegmentNamingStrategy(applicationName);
        }

        @Bean
        @ConditionalOnMissingBean
        public ClientSegmentMutator clientSegmentMutator() {
            return new UnsampledClientSegmentMutator();
        }

        @Bean
        public SqsAsyncClientProvider xraySqsAsyncClientProvider(
            final SqsAsyncClient defaultClient,
            @Qualifier("sqsXrayRecorder") final AWSXRayRecorder recorder,
            final ClientSegmentNamingStrategy clientSegmentNamingStrategy,
            final ClientSegmentMutator clientSegmentMutator
        ) {
            final XrayWrappedSqsAsyncClient xrayDefaultClient = new XrayWrappedSqsAsyncClient(
                XrayWrappedSqsAsyncClient
                    .Options.builder()
                    .delegate(defaultClient)
                    .recorder(recorder)
                    .segmentNamingStrategy(clientSegmentNamingStrategy)
                    .segmentMutator(clientSegmentMutator)
                    .build()
            );

            return new DefaultSqsAsyncClientProvider(xrayDefaultClient);
        }
    }
}
