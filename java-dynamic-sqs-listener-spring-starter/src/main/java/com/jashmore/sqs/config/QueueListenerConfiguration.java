package com.jashmore.sqs.config;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.annotation.EnableQueueListeners;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DefaultArgumentResolverService;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration that will be enabled if the {@link EnableQueueListeners @EnableQueueListeners} annotation has been applied to the application.
 *
 * <p>The configuration for the application has been designed to allow for replacements by the consumer so that they can extend the framework, integrate into
 * existing beans in the application or replace implementations of the framework with their own.
 */
@Configuration
@ComponentScan("com.jashmore.sqs")
public class QueueListenerConfiguration {
    /**
     * The default {@link AmazonSQSAsync} that will be used if the application does not provide their own.
     *
     * <p>This will set up the default client which will be configured using the {@link com.amazonaws.auth.DefaultAWSCredentialsProviderChain} and
     * {@link com.amazonaws.regions.DefaultAwsRegionProviderChain} chain which finds configuration properties for the client from environment variables, etc.
     *
     * @return a default {@link AmazonSQSAsync} that should be used if none were provided
     * @see AmazonSQSAsyncClientBuilder#defaultClient() for more details about how to use this default client
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(AmazonSQSAsync.class)
    public AmazonSQSAsync amazonSqsAsync() {
        return new AmazonSQSBufferedAsyncClient(AmazonSQSAsyncClientBuilder.defaultClient());
    }

    /**
     * The default {@link PayloadMapper} that will be able to deserialise message payloads using the Jackson {@link ObjectMapper}.
     *
     * <p>If the payloads of the message cannot be deserialised by an {@link ObjectMapper} or the default {@link PayloadMapper} implementation is not up to
     * scratch, the consumer can supply their own {@link PayloadMapper} bean and this one will not be used.
     *
     * @return the payload mapper for message payload deserialisation
     */
    @Bean
    @ConditionalOnMissingBean(PayloadMapper.class)
    public PayloadMapper payloadMapper() {
        return new JacksonPayloadMapper(new ObjectMapper());
    }

    /**
     * A {@link DefaultArgumentResolverService} is used if no other {@link ArgumentResolverService} has been supplied by the consumer which will
     * define how the basic parameters can be resolved for a message.
     *
     * <p>If more types of parameters are needing to be able to be resolved or the {@link DefaultArgumentResolverService} does not work as required,
     * the application can supply their own resolver. An example of doing this is the following:
     *
     * <pre class="code">
     * &#064;Bean
     * public ArgumentResolverService myOwnArgumentResolverService() {
     *     return new DelegatingArgumentResolverService(
     *             ImmutableSet.of(new PayloadArgumentResolver(new JacksonPayloadMapper(new ObjectMapper())))
     *     );
     * }
     * </pre>
     *
     * @param payloadMapper  the mapper used to map the message body to a payload in th method
     * @param amazonSqsAsync the sqs client to communicate with the SQS queue
     * @return an {@link ArgumentResolverService} that will be able to resolve method parameters for message processing
     */
    @Bean
    @ConditionalOnMissingBean(ArgumentResolverService.class)
    public ArgumentResolverService argumentResolverService(final PayloadMapper payloadMapper,
                                                           final AmazonSQSAsync amazonSqsAsync) {
        return new DefaultArgumentResolverService(payloadMapper, amazonSqsAsync);
    }
}
