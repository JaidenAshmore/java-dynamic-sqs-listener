package com.jashmore.sqs.spring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.annotations.core.basic.BasicAnnotationMessageListenerContainerFactory;
import com.jashmore.sqs.annotations.core.basic.QueueListenerParser;
import com.jashmore.sqs.annotations.core.fifo.FifoAnnotationMessageListenerContainerFactory;
import com.jashmore.sqs.annotations.core.fifo.FifoQueueListenerParser;
import com.jashmore.sqs.annotations.core.prefetch.PrefetchingAnnotationMessageListenerContainerFactory;
import com.jashmore.sqs.annotations.core.prefetch.PrefetchingQueueListenerParser;
import com.jashmore.sqs.annotations.decorator.visibilityextender.AutoVisibilityExtenderMessageProcessingDecoratorFactory;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DelegatingArgumentResolverService;
import com.jashmore.sqs.argument.attribute.MessageAttributeArgumentResolver;
import com.jashmore.sqs.argument.attribute.MessageSystemAttributeArgumentResolver;
import com.jashmore.sqs.argument.message.MessageArgumentResolver;
import com.jashmore.sqs.argument.messageid.MessageIdArgumentResolver;
import com.jashmore.sqs.argument.payload.PayloadArgumentResolver;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.client.DefaultPlaceholderQueueResolver;
import com.jashmore.sqs.client.DefaultSqsAsyncClientProvider;
import com.jashmore.sqs.client.QueueResolver;
import com.jashmore.sqs.client.SqsAsyncClientProvider;
import com.jashmore.sqs.container.MessageListenerContainer;
import com.jashmore.sqs.container.MessageListenerContainerCoordinator;
import com.jashmore.sqs.container.MessageListenerContainerFactory;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import com.jashmore.sqs.decorator.MessageProcessingDecoratorFactory;
import com.jashmore.sqs.placeholder.PlaceholderResolver;
import com.jashmore.sqs.processor.DecoratingMessageProcessorFactory;
import com.jashmore.sqs.spring.container.SpringMessageListenerContainerCoordinator;
import com.jashmore.sqs.spring.container.SpringMessageListenerContainerCoordinatorProperties;
import com.jashmore.sqs.spring.container.StaticSpringMessageListenerContainerCoordinatorProperties;
import com.jashmore.sqs.spring.jackson.SqsListenerObjectMapperSupplier;
import com.jashmore.sqs.spring.placeholder.SpringPlaceholderResolver;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * The configuration for the application has been designed to allow for replacements by the consumer so that they can extend the framework, integrate into
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
    @ConditionalOnMissingBean({ SqsAsyncClient.class, SqsAsyncClientProvider.class })
    public SqsAsyncClient sqsAsyncClient() {
        return SqsAsyncClient.create();
    }

    /**
     * Provides the {@link SqsAsyncClientProvider} which is used to provide the relevant {@link SqsAsyncClient} as there could be multiple AWS
     * Accounts/Credentials being used.
     *
     * <p>When a user provides their own bean of this class they provide all of the {@link SqsAsyncClient}s that will be used, such as defining their
     * own default {@link SqsAsyncClient} and all other identifier clients, see {@link SqsAsyncClientProvider#getClient(String)}.
     *
     * <p>The user may define their own {@link SqsAsyncClient} which will be used instead of the one provided by {@link #sqsAsyncClient()} if they only
     * want to use the default client and don't want to be able to pick one of multiple clients.
     *
     * @param defaultClient the default client
     * @return the provider for obtains {@link SqsAsyncClient}s, in this case only the default client
     */
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Bean
    @ConditionalOnMissingBean({ SqsAsyncClientProvider.class })
    public SqsAsyncClientProvider sqsAsyncClientProvider(final SqsAsyncClient defaultClient) {
        return new DefaultSqsAsyncClientProvider(defaultClient, Collections.emptyMap());
    }

    /**
     * Contains all the configuration that needs to supplied if there is no {@link ArgumentResolverService} class defined.
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
         * @param argumentResolvers the argument resolvers available in the application
         * @return an {@link ArgumentResolverService} that will be able to resolve method parameters for message processing
         */
        @Bean
        public ArgumentResolverService defaultArgumentResolverService(final List<ArgumentResolver<?>> argumentResolvers) {
            return new DelegatingArgumentResolverService(argumentResolvers);
        }

        /**
         * The following are the core {@link ArgumentResolver}s that will be present in the application.
         */
        @Configuration
        public static class CoreArgumentResolversConfiguration {

            @Bean
            @ConditionalOnMissingBean
            public SqsListenerObjectMapperSupplier objectMapperSupplier(@Autowired(required = false) final ObjectMapper objectMapper) {
                final ObjectMapper actualObjectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
                return () -> actualObjectMapper;
            }

            @Bean
            @ConditionalOnMissingBean(PayloadArgumentResolver.class)
            public PayloadArgumentResolver payloadArgumentResolver(final SqsListenerObjectMapperSupplier objectMapperSupplier) {
                return new PayloadArgumentResolver(new JacksonPayloadMapper(objectMapperSupplier.get()));
            }

            @Bean
            public MessageIdArgumentResolver messageIdArgumentResolver() {
                return new MessageIdArgumentResolver();
            }

            @Bean
            public MessageSystemAttributeArgumentResolver messageSystemAttributeArgumentResolver() {
                return new MessageSystemAttributeArgumentResolver();
            }

            @Bean
            @ConditionalOnMissingBean(MessageAttributeArgumentResolver.class)
            public MessageAttributeArgumentResolver messageAttributeArgumentResolver(
                final SqsListenerObjectMapperSupplier objectMapperSupplier
            ) {
                return new MessageAttributeArgumentResolver(objectMapperSupplier.get());
            }

            @Bean
            public MessageArgumentResolver messageArgumentResolver() {
                return new MessageArgumentResolver();
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(PlaceholderResolver.class)
    public PlaceholderResolver placeholderResolver(final Environment environment) {
        return new SpringPlaceholderResolver(environment);
    }

    /**
     * The default provided {@link QueueResolver} that can be used if it is not overridden by a user defined bean.
     *
     * @param placeholderResolver the environment for this spring application
     * @return the default service used for queue resolution
     */
    @Bean
    @ConditionalOnMissingBean(QueueResolver.class)
    public QueueResolver queueResolver(final PlaceholderResolver placeholderResolver) {
        return new DefaultPlaceholderQueueResolver(placeholderResolver);
    }

    /**
     * Configuration used with regard to searching the application code for methods that need to be wrapped in {@link MessageListenerContainer}s.
     *
     * <p>If the user has defined their own {@link MessageListenerContainerCoordinator} this default configuration will not be used. This allows the
     * consumer to configure the wrapping of their bean methods to how they feel is optimal. It can be also done if there is a bug in the existing default
     * {@link MessageListenerContainerCoordinator} provided.
     */
    @Configuration
    @ConditionalOnMissingBean(MessageListenerContainerCoordinator.class)
    public static class QueueWrappingConfiguration {

        /**
         * The configuration properties for the {@link SpringMessageListenerContainerCoordinator}.
         *
         * @return the default configuration properties for the coordinator
         */
        @Bean
        @ConditionalOnMissingBean(SpringMessageListenerContainerCoordinatorProperties.class)
        public SpringMessageListenerContainerCoordinatorProperties defaultMessageListenerContainerCoordinatorProperties() {
            return StaticSpringMessageListenerContainerCoordinatorProperties.builder().isAutoStartContainersEnabled(true).build();
        }

        /**
         * The default {@link MessageListenerContainerCoordinator} that will be provided if it has not been overridden by the consumer.
         *
         * <p>This will use any {@link MessageListenerContainerFactory}s defined in the spring context to wrap any bean methods with a
         * {@link MessageListenerContainer}.
         *
         * @param properties                        the configuration properties for this coordinator
         * @param messageListenerContainerFactories the wrappers provided in the context
         * @return the default {@link MessageListenerContainerCoordinator}
         */
        @Bean
        public MessageListenerContainerCoordinator messageListenerContainerCoordinator(
            final SpringMessageListenerContainerCoordinatorProperties properties,
            final List<MessageListenerContainerFactory> messageListenerContainerFactories
        ) {
            return new SpringMessageListenerContainerCoordinator(properties, messageListenerContainerFactories);
        }

        /**
         * Contains all of the {@link MessageProcessingDecoratorFactory}s that can be used to attach {@link MessageProcessingDecorator}s to individual
         * message listeners.
         */
        @Configuration
        @ConditionalOnMissingBean(DecoratingMessageProcessorFactory.class)
        public static class MessageProcessingDecoratorFactories {

            @Bean
            public DecoratingMessageProcessorFactory decoratingMessageProcessorFactory(
                final List<MessageProcessingDecorator> globalDecorators,
                final List<MessageProcessingDecoratorFactory<? extends MessageProcessingDecorator>> messageProcessingDecoratorFactories
            ) {
                return new DecoratingMessageProcessorFactory(globalDecorators, messageProcessingDecoratorFactories);
            }

            @Bean
            public AutoVisibilityExtenderMessageProcessingDecoratorFactory autoVisibilityExtendMessageProcessingDecoratorFactory(
                final PlaceholderResolver placeholderResolver
            ) {
                return new AutoVisibilityExtenderMessageProcessingDecoratorFactory(placeholderResolver);
            }
        }

        /**
         * Contains all the core {@link MessageListenerContainerFactory} implementations that should be enabled by default.
         *
         * <p>The consumer can provide any other {@link MessageListenerContainerFactory} beans in their context and these will be included in the automatic
         * wrapping of the methods by the {@link #messageListenerContainerCoordinator(SpringMessageListenerContainerCoordinatorProperties, List)} bean.
         */
        @Configuration
        public static class MessageListenerContainerFactoryConfiguration {

            @Bean
            @ConditionalOnMissingBean(QueueListenerParser.class)
            public QueueListenerParser queueListenerParser(final PlaceholderResolver placeholderResolver) {
                return new QueueListenerParser(placeholderResolver);
            }

            @Bean
            public MessageListenerContainerFactory basicMessageListenerContainerFactory(
                final ArgumentResolverService argumentResolverService,
                final SqsAsyncClientProvider sqsAsyncClientProvider,
                final QueueResolver queueResolver,
                final QueueListenerParser queueListenerParser,
                final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory
            ) {
                return new BasicAnnotationMessageListenerContainerFactory(
                    argumentResolverService,
                    sqsAsyncClientProvider,
                    queueResolver,
                    queueListenerParser,
                    decoratingMessageProcessorFactory
                );
            }

            @Bean
            @ConditionalOnMissingBean(PrefetchingQueueListenerParser.class)
            public PrefetchingQueueListenerParser prefetchingQueueListenerParser(final PlaceholderResolver placeholderResolver) {
                return new PrefetchingQueueListenerParser(placeholderResolver);
            }

            @Bean
            public MessageListenerContainerFactory prefetchingMessageListenerContainerFactory(
                final ArgumentResolverService argumentResolverService,
                final SqsAsyncClientProvider sqsAsyncClientProvider,
                final QueueResolver queueResolver,
                final PrefetchingQueueListenerParser prefetchingQueueListenerParser,
                final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory
            ) {
                return new PrefetchingAnnotationMessageListenerContainerFactory(
                    argumentResolverService,
                    sqsAsyncClientProvider,
                    queueResolver,
                    prefetchingQueueListenerParser,
                    decoratingMessageProcessorFactory
                );
            }

            @Bean
            @ConditionalOnMissingBean(FifoQueueListenerParser.class)
            public FifoQueueListenerParser fifoMessageListenerParser(final PlaceholderResolver placeholderResolver) {
                return new FifoQueueListenerParser(placeholderResolver);
            }

            @Bean
            public MessageListenerContainerFactory fifoMessageListenerContainerFactory(
                final ArgumentResolverService argumentResolverService,
                final SqsAsyncClientProvider sqsAsyncClientProvider,
                final QueueResolver queueResolver,
                final FifoQueueListenerParser fifoQueueListenerParser,
                final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory
            ) {
                return new FifoAnnotationMessageListenerContainerFactory(
                    argumentResolverService,
                    sqsAsyncClientProvider,
                    queueResolver,
                    fifoQueueListenerParser,
                    decoratingMessageProcessorFactory
                );
            }
        }
    }
}
