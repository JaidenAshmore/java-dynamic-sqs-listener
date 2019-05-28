package com.jashmore.sqs.spring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DelegatingArgumentResolverService;
import com.jashmore.sqs.argument.attribute.MessageAttributeArgumentResolver;
import com.jashmore.sqs.argument.messageid.MessageIdArgumentResolver;
import com.jashmore.sqs.argument.payload.PayloadArgumentResolver;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.visibility.VisibilityExtenderArgumentResolver;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.spring.DefaultQueueContainerService;
import com.jashmore.sqs.spring.QueueContainerService;
import com.jashmore.sqs.spring.QueueWrapper;
import com.jashmore.sqs.spring.container.basic.QueueListenerWrapper;
import com.jashmore.sqs.spring.container.batching.BatchingQueueListenerWrapper;
import com.jashmore.sqs.spring.container.prefetch.PrefetchingQueueListenerWrapper;
import com.jashmore.sqs.spring.queue.DefaultQueueResolverService;
import com.jashmore.sqs.spring.queue.QueueResolverService;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.List;
import java.util.Set;

/**
 * AutoConfiguration that will be enabled if the {@link EnableAutoConfiguration @EnableAutoConfiguration} annotation has been applied to the application.
 *
 * <p>The configuration for the application has been designed to allow for replacements by the consumer so that they can extend the framework, integrate into
 * existing beans in the application or replace implementations of the framework with their own.
 */
@Configuration
public class QueueListenerConfiguration {
    /**
     * The default {@link SqsAsyncClient} that will be used if the application does not provide their own.
     *
     * <p>This will set up the default client which will be configured using the {@link software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider} and
     * {@link software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain} chain which finds configuration properties for the client from
     * system variables, etc.
     *
     * @return a default {@link SqsAsyncClient} that should be used if none were provided
     * @see SqsAsyncClient#create() for more details about how to use this default client
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(SqsAsyncClient.class)
    public SqsAsyncClient sqsAsyncClient() {
        return SqsAsyncClient.create();
    }

    /**
     * Contains all of the configuration that needs to supplied if there is no {@link ArgumentResolverService} class defined.
     *
     * <p>Note that if a custom {@link ArgumentResolverService} is defined by the user, none of the {@link ArgumentResolver}s will be included in the context
     * of the application. This allows the ability of the consumer to limit the amount of code needed to resolve arguments to only the certain criteria that
     * they care about. For example, if they only use the {@link PayloadArgumentResolver} they could override this default implementation to only use this
     * resolver:
     *
     * <pre class="code">
     * &#064;Bean
     * public ArgumentResolverService myOwnArgumentResolverService() {
     *     return new DelegatingArgumentResolverService(
     *             ImmutableSet.of(new PayloadArgumentResolver(new JacksonPayloadMapper(new ObjectMapper())))
     *     );
     * }
     * </pre>
     */
    @Configuration
    @AutoConfigureAfter(QueueListenerConfiguration.class)
    @ConditionalOnMissingBean(ArgumentResolverService.class)
    public static class ArgumentResolutionConfiguration {
        /**
         * The {@link ArgumentResolverService} used if none are defined by the consumer of this framework.
         *
         * <p>This will use any of the {@link ArgumentResolver}s defined in the context and therefore more can be supplied by the consumer if they desire. For
         * example, they want to define a type of payload argument resolution that will extract certain fields from the payload. They can define their
         * {@link ArgumentResolver} bean in the context and it will be included in this {@link ArgumentResolverService}.
         *
         * @param argumentResolvers  the argument resolvers available in the application
         * @return an {@link ArgumentResolverService} that will be able to resolve method parameters for message processing
         */
        @Bean
        public ArgumentResolverService defaultArgumentResolverService(final Set<ArgumentResolver<?>> argumentResolvers) {
            return new DelegatingArgumentResolverService(argumentResolvers);
        }

        /**
         * The following are the core {@link ArgumentResolver}s that will be present in the application.
         */
        @Configuration
        public static class CoreArgumentResolversConfiguration {
            @Bean
            @ConditionalOnMissingBean(ObjectMapper.class)
            public ObjectMapper objectMapper() {
                return new ObjectMapper();
            }

            @Bean
            @ConditionalOnMissingBean(PayloadArgumentResolver.class)
            public PayloadArgumentResolver payloadArgumentResolver(final ObjectMapper objectMapper) {
                return new PayloadArgumentResolver(new JacksonPayloadMapper(objectMapper));
            }

            @Bean
            public MessageIdArgumentResolver messageIdArgumentResolver() {
                return new MessageIdArgumentResolver();
            }

            @Bean
            public VisibilityExtenderArgumentResolver visibilityExtenderArgumentResolver(final SqsAsyncClient sqsAsyncClient) {
                return new VisibilityExtenderArgumentResolver(sqsAsyncClient);
            }

            @Bean
            @ConditionalOnMissingBean(MessageAttributeArgumentResolver.class)
            public MessageAttributeArgumentResolver messageAttributeArgumentResolver(final ObjectMapper objectMapper) {
                return new MessageAttributeArgumentResolver(objectMapper);
            }
        }
    }

    /**
     * The default provided {@link QueueResolverService} that can be used if it not overriden by a user defined bean.
     *
     * @param sqsAsyncClient client to communicate with the SQS queues
     * @param environment    the environment for this spring application
     * @return the default service used for queue resolution
     */
    @Bean
    @ConditionalOnMissingBean(QueueResolverService.class)
    public QueueResolverService queueResolverService(final SqsAsyncClient sqsAsyncClient, final Environment environment) {
        return new DefaultQueueResolverService(sqsAsyncClient, environment);
    }

    /**
     * Configuration used in regards to searching the application code for methods that need to be wrapped in {@link MessageListenerContainer}s.
     *
     * <p>If the user has defined their own {@link QueueContainerService} this default configuration will not be used. This allows the consumer to configure
     * the wrapping of their bean methods to how they feel is optimal. It can be also done if there is a bug in the existing default
     * {@link QueueContainerService} provided.
     */
    @Configuration
    @ConditionalOnMissingBean(QueueContainerService.class)
    public static class QueueWrappingConfiguration {

        /**
         * The default {@link QueueContainerService} that will be provided if it has not been overridden by the consumer.
         *
         * <p>This will use any {@link QueueWrapper}s defined in the spring context to wrap any bean methods with a {@link MessageListenerContainer}.
         *
         * @param queueWrappers the wrappers provided in the context
         * @return the default {@link QueueContainerService}
         */
        @Bean
        public QueueContainerService queueContainerService(final List<QueueWrapper> queueWrappers) {
            return new DefaultQueueContainerService(queueWrappers);
        }

        /**
         * Contains all of the core {@link QueueWrapper} implementations that should be enabled by default.
         *
         * <p>The consumer can provide any other {@link QueueWrapper} beans in their context and these will be included in the automatic wrapping of the methods
         * by the {@link #queueContainerService(List)} bean.
         */
        @Configuration
        public static class QueueWrapperConfiguration {
            @Bean
            public QueueWrapper coreProvidedQueueListenerWrapper(final ArgumentResolverService argumentResolverService,
                                                                 final SqsAsyncClient sqsAsyncClient,
                                                                 final QueueResolverService queueResolverService,
                                                                 final Environment environment) {
                return new QueueListenerWrapper(argumentResolverService, sqsAsyncClient, queueResolverService, environment);
            }

            @Bean
            public QueueWrapper coreProvidedPrefetchingQueueListenerWrapper(final ArgumentResolverService argumentResolverService,
                                                                            final SqsAsyncClient sqsAsyncClient,
                                                                            final QueueResolverService queueResolverService,
                                                                            final Environment environment) {
                return new PrefetchingQueueListenerWrapper(argumentResolverService, sqsAsyncClient, queueResolverService, environment);
            }

            @Bean
            public QueueWrapper coreProvidedBatchingQueueListenerWrapper(final ArgumentResolverService argumentResolverService,
                                                                         final SqsAsyncClient sqsAsyncClient,
                                                                         final QueueResolverService queueResolverService,
                                                                         final Environment environment) {
                return new BatchingQueueListenerWrapper(argumentResolverService, sqsAsyncClient, queueResolverService, environment);
            }
        }
    }
}
