package com.jashmore.sqs.micronaut.configuration;

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
import com.jashmore.sqs.container.MessageListenerContainerFactory;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import com.jashmore.sqs.decorator.MessageProcessingDecoratorFactory;
import com.jashmore.sqs.micronaut.jackson.SqsListenerObjectMapperSupplier;
import com.jashmore.sqs.micronaut.placeholder.MicronautPlaceholderResolver;
import com.jashmore.sqs.placeholder.PlaceholderResolver;
import com.jashmore.sqs.processor.DecoratingMessageProcessorFactory;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;

import java.util.Collections;
import java.util.List;

@Factory
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
    @Singleton
    @Secondary
    @Bean(preDestroy = "close")
    public SqsAsyncClient sqsAsyncClient(
            @Nullable AwsRegionProvider awsRegionProvider
    ) {
        SqsAsyncClientBuilder builder = SqsAsyncClient.builder();
        if (awsRegionProvider != null) {
            builder.region(awsRegionProvider.getRegion());
        }
        return builder.build();
    }

    /**
     * Provides the {@link SqsAsyncClientProvider} which is used to provide the relevant {@link SqsAsyncClient} as there could be multiple AWS
     * Accounts/Credentials being used.
     *
     * <p>When a user provides their own bean of this class they provide all of the {@link SqsAsyncClient}s that will be used, such as defining their
     * own default {@link SqsAsyncClient} and all other identifier clients, see {@link SqsAsyncClientProvider#getClient(String)}.
     *
     * <p>The user may define their own {@link SqsAsyncClient} which will be used instead of the one provided by
     * {@link #sqsAsyncClient(AwsRegionProvider)} if they only
     * want to use the default client and don't want to be able to pick one of multiple clients.
     *
     * @param defaultClient the default client
     * @return the provider for obtains {@link SqsAsyncClient}s, in this case only the default client
     */
    @Singleton
    @Secondary
    public SqsAsyncClientProvider sqsAsyncClientProvider(final SqsAsyncClient defaultClient) {
        return new DefaultSqsAsyncClientProvider(defaultClient, Collections.emptyMap());
    }

    @Factory
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
        @Singleton
        public ArgumentResolverService defaultArgumentResolverService(final List<ArgumentResolver<?>> argumentResolvers) {
            return new DelegatingArgumentResolverService(argumentResolvers);
        }

        @Factory
        public static class CoreArgumentResolversConfiguration {

            @Singleton
            public SqsListenerObjectMapperSupplier objectMapperSupplier(@Nullable final ObjectMapper objectMapper) {
                final ObjectMapper actualObjectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
                return () -> actualObjectMapper;
            }

            @Singleton
            public PayloadArgumentResolver payloadArgumentResolver(
                    final SqsListenerObjectMapperSupplier objectMapperSupplier
            ) {
                return new PayloadArgumentResolver(new JacksonPayloadMapper(objectMapperSupplier.get()));
            }

            @Singleton
            public MessageIdArgumentResolver messageIdArgumentResolver() {
                return new MessageIdArgumentResolver();
            }

            @Singleton
            public MessageSystemAttributeArgumentResolver messageSystemAttributeArgumentResolver() {
                return new MessageSystemAttributeArgumentResolver();
            }

            @Singleton
            public MessageAttributeArgumentResolver messageAttributeArgumentResolver(
                    final SqsListenerObjectMapperSupplier objectMapperSupplier
            ) {
                return new MessageAttributeArgumentResolver(objectMapperSupplier.get());
            }

            @Singleton
            public MessageArgumentResolver messageArgumentResolver() {
                return new MessageArgumentResolver();
            }
        }
    }

    @Singleton
    public PlaceholderResolver placeholderResolver(final Environment environment) {
        return new MicronautPlaceholderResolver(environment);
    }

    /**
     * The default provided {@link QueueResolver} that can be used if it is not overridden by a user defined bean.
     *
     * @param placeholderResolver the environment for this spring application
     * @return the default service used for queue resolution
     */
    @Singleton
    public QueueResolver queueResolver(final PlaceholderResolver placeholderResolver) {
        return new DefaultPlaceholderQueueResolver(placeholderResolver);
    }

    @Factory
    public static class QueueWrappingConfiguration {

        @Factory
        public static class MessageProcessingDecoratorFactories {

            @Singleton
            public DecoratingMessageProcessorFactory decoratingMessageProcessorFactory(
                    final List<MessageProcessingDecorator> globalDecorators,
                    final List<MessageProcessingDecoratorFactory<? extends MessageProcessingDecorator>> messageProcessingDecoratorFactories
            ) {
                return new DecoratingMessageProcessorFactory(globalDecorators, messageProcessingDecoratorFactories);
            }

            @Singleton
            public AutoVisibilityExtenderMessageProcessingDecoratorFactory autoVisibilityExtendMessageProcessingDecoratorFactory(
                    final PlaceholderResolver placeholderResolver
            ) {
                return new AutoVisibilityExtenderMessageProcessingDecoratorFactory(placeholderResolver);
            }
        }

        /**
         * Contains all of the core {@link MessageListenerContainerFactory} implementations that should be enabled by default.
         *
         * <p>The consumer can provide any other {@link MessageListenerContainerFactory} beans in their context and
         * these will be included in the automatic wrapping of the methods by the
         * {@link com.jashmore.sqs.container.MessageListenerContainerCoordinator} bean.
         */
        @Factory
        public static class MessageListenerContainerFactoryConfiguration {

            @Singleton
            public QueueListenerParser queueListenerParser(final PlaceholderResolver placeholderResolver) {
                return new QueueListenerParser(placeholderResolver);
            }

            @Singleton
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

            @Singleton
            public PrefetchingQueueListenerParser prefetchingQueueListenerParser(final PlaceholderResolver placeholderResolver) {
                return new PrefetchingQueueListenerParser(placeholderResolver);
            }

            @Singleton
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

            @Singleton
            public FifoQueueListenerParser fifoMessageListenerParser(final PlaceholderResolver placeholderResolver) {
                return new FifoQueueListenerParser(placeholderResolver);
            }

            @Singleton
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
