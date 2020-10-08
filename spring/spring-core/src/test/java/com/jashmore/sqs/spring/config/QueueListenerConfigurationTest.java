package com.jashmore.sqs.spring.config;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DelegatingArgumentResolverService;
import com.jashmore.sqs.argument.attribute.MessageAttributeArgumentResolver;
import com.jashmore.sqs.argument.attribute.MessageSystemAttributeArgumentResolver;
import com.jashmore.sqs.argument.message.MessageArgumentResolver;
import com.jashmore.sqs.argument.messageid.MessageIdArgumentResolver;
import com.jashmore.sqs.argument.payload.PayloadArgumentResolver;
import com.jashmore.sqs.container.batching.BatchingMessageListenerContainerProperties;
import com.jashmore.sqs.container.fifo.FifoMessageListenerContainerProperties;
import com.jashmore.sqs.container.prefetching.PrefetchingMessageListenerContainerProperties;
import com.jashmore.sqs.decorator.MessageProcessingDecorator;
import com.jashmore.sqs.processor.MessageProcessor;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.container.DefaultMessageListenerContainerCoordinator;
import com.jashmore.sqs.spring.container.DefaultMessageListenerContainerCoordinatorProperties;
import com.jashmore.sqs.spring.container.MessageListenerContainerCoordinator;
import com.jashmore.sqs.spring.container.MessageListenerContainerFactory;
import com.jashmore.sqs.spring.container.basic.BasicMessageListenerContainerFactory;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.spring.container.basic.QueueListenerParser;
import com.jashmore.sqs.spring.container.fifo.FifoMessageListenerContainerFactory;
import com.jashmore.sqs.spring.container.fifo.FifoQueueListener;
import com.jashmore.sqs.spring.container.fifo.FifoQueueListenerParser;
import com.jashmore.sqs.spring.container.prefetch.PrefetchingMessageListenerContainerFactory;
import com.jashmore.sqs.spring.container.prefetch.PrefetchingQueueListener;
import com.jashmore.sqs.spring.container.prefetch.PrefetchingQueueListenerParser;
import com.jashmore.sqs.spring.decorator.MessageProcessingDecoratorFactory;
import com.jashmore.sqs.spring.jackson.SqsListenerObjectMapperSupplier;
import com.jashmore.sqs.spring.processor.DecoratingMessageProcessorFactory;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

@SuppressWarnings({ "unchecked", "rawtypes" })
class QueueListenerConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(QueueListenerConfiguration.class));

    @Nested
    class SqsAsyncClientBean {

        @Test
        void whenUserDoesNotProvidedSqsAsyncClientTheDefaultIsUsed() {
            contextRunner
                .withSystemProperties("aws.region:localstack")
                .run(
                    context -> {
                        assertThat(context).hasSingleBean(SqsAsyncClient.class);
                        assertThat(context.getBean(SqsAsyncClient.class))
                            .isSameAs(context.getBean(QueueListenerConfiguration.class).sqsAsyncClient());
                    }
                );
        }

        @Test
        void whenUserProvidesSqsAsyncClientTheDefaultIsNotUsed() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run(
                    context -> {
                        assertThat(context).hasSingleBean(SqsAsyncClient.class);
                        assertThat(context.getBean(SqsAsyncClient.class))
                            .isSameAs(context.getBean(UserConfigurationWithSqsClient.class).userDefinedSqsAsyncClient());
                    }
                );
        }
    }

    @Nested
    class SqsAsyncClientProviderBean {

        @Test
        void whenNoSqsAsyncClientProviderADefaultImplementationIsCreated() {
            contextRunner
                .withSystemProperties("aws.region:localstack")
                .run(
                    context -> {
                        assertThat(context).hasSingleBean(SqsAsyncClientProvider.class);
                        assertThat(context.getBean(SqsAsyncClientProvider.class))
                            .isSameAs(context.getBean(QueueListenerConfiguration.class).sqsAsyncClientProvider(null));
                    }
                );
        }

        @Test
        void whenNoCustomSqsAsyncClientProviderAndSqsAsyncClientDefaultSqsAsyncClientIsTheSqsAsyncClientProvidersDefault() {
            contextRunner
                .withSystemProperties("aws.region:localstack")
                .run(
                    context -> {
                        final SqsAsyncClientProvider sqsAsyncClientProvider = context.getBean(SqsAsyncClientProvider.class);
                        final SqsAsyncClient expectedDefault = context.getBean(SqsAsyncClient.class);
                        assertThat(sqsAsyncClientProvider.getDefaultClient()).contains(expectedDefault);
                    }
                );
        }

        @Test
        void whenCustomSqsAsyncClientProvidedTheDefaultSqsAsyncClientProviderUsesThisAsTheDefault() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run(
                    context -> {
                        final SqsAsyncClientProvider sqsAsyncClientProvider = context.getBean(SqsAsyncClientProvider.class);
                        final SqsAsyncClient userDefinedSqsAsyncClient = context
                            .getBean(UserConfigurationWithSqsClient.class)
                            .userDefinedSqsAsyncClient();
                        assertThat(sqsAsyncClientProvider.getDefaultClient()).contains(userDefinedSqsAsyncClient);
                    }
                );
        }

        @Test
        void whenCustomSqsAsyncClientProviderProvidedNoSqsAsyncClientBeanIsBuilt() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClientProvider.class)
                .run(context -> assertThat(context).doesNotHaveBean(SqsAsyncClient.class));
        }

        @Test
        void whenCustomSqsAsyncClientProviderThatIsUsedInsteadOfTheDefault() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClientProvider.class)
                .run(
                    context ->
                        assertThat(context.getBean(SqsAsyncClientProvider.class))
                            .isSameAs(context.getBean(UserConfigurationWithSqsClientProvider.class).userDefinedSqsAsyncClientProvider())
                );
        }
    }

    @Nested
    class ObjectMapperBean {

        @Test
        void userConfigurationWithNoObjectMapperSupplierWillProvideOwnForArgumentResolvers() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run(
                    context -> {
                        final SqsListenerObjectMapperSupplier objectMapperSupplier = context.getBean(SqsListenerObjectMapperSupplier.class);
                        assertThat(objectMapperSupplier.get()).isNotNull();
                    }
                );
        }

        @Test
        void objectMapperBeanPresentWillBeUsedInDefaultObjectMapperSupplier() {
            final ObjectMapper mockObjectMapper = mock(ObjectMapper.class);
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .withBean(ObjectMapper.class, () -> mockObjectMapper)
                .run(
                    context -> {
                        final SqsListenerObjectMapperSupplier objectMapperSupplier = context.getBean(SqsListenerObjectMapperSupplier.class);
                        assertThat(objectMapperSupplier.get()).isSameAs(mockObjectMapper);
                    }
                );
        }

        @Test
        void customObjectMapperSupplierWillOverwriteDefault() {
            final ObjectMapper mockObjectMapper = mock(ObjectMapper.class);
            final SqsListenerObjectMapperSupplier customObjectMapperSupplier = () -> mockObjectMapper;
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .withBean(SqsListenerObjectMapperSupplier.class, () -> customObjectMapperSupplier)
                .run(context -> assertThat(context.getBean(SqsListenerObjectMapperSupplier.class)).isSameAs(customObjectMapperSupplier));
        }
    }

    @Nested
    class PayloadArgumentResolverBean {

        @Test
        void whenUserDoesNotProvideAPayloadArgumentResolverTheDefaultIsUsed() {
            contextRunner
                .withSystemProperties("aws.region:localstack")
                .run(
                    context -> {
                        final SqsListenerObjectMapperSupplier objectMapperSupplier = context.getBean(SqsListenerObjectMapperSupplier.class);
                        assertThat(context).hasSingleBean(PayloadArgumentResolver.class);
                        assertThat(context.getBean(PayloadArgumentResolver.class))
                            .isSameAs(
                                context
                                    .getBean(
                                        QueueListenerConfiguration.ArgumentResolutionConfiguration.CoreArgumentResolversConfiguration.class
                                    )
                                    .payloadArgumentResolver(objectMapperSupplier)
                            );
                    }
                );
        }

        @Test
        void whenUserProvidesAPayloadArgumentResolverTheDefaultIsNotUsed() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithPayloadArgumentResolverDefined.class)
                .run(
                    context -> {
                        assertThat(context).hasSingleBean(PayloadArgumentResolver.class);
                        assertThat(context.getBean(PayloadArgumentResolver.class))
                            .isSameAs(context.getBean(UserConfigurationWithPayloadArgumentResolverDefined.class).payloadArgumentResolver());
                    }
                );
        }
    }

    @Nested
    class MessageAttributeArgumentResolverBean {

        @Test
        void whenNoUserProvidesAMessageAttributeArgumentResolverTheDefaultIsUsed() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithMessageAttributeArgumentResolverDefined.class)
                .run(
                    context -> {
                        assertThat(context).hasSingleBean(MessageAttributeArgumentResolver.class);
                        assertThat(context.getBean(MessageAttributeArgumentResolver.class))
                            .isSameAs(
                                context
                                    .getBean(UserConfigurationWithMessageAttributeArgumentResolverDefined.class)
                                    .messageAttributeArgumentResolver()
                            );
                    }
                );
        }

        @Test
        void whenUserProvidesAMessageAttributeArgumentResolverTheDefaultIsNotUsed() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithMessageAttributeArgumentResolverDefined.class)
                .run(
                    context -> {
                        assertThat(context).hasSingleBean(MessageAttributeArgumentResolver.class);
                        assertThat(context.getBean(MessageAttributeArgumentResolver.class))
                            .isSameAs(
                                context
                                    .getBean(UserConfigurationWithMessageAttributeArgumentResolverDefined.class)
                                    .messageAttributeArgumentResolver()
                            );
                    }
                );
        }
    }

    @Nested
    class ArgumentResolverServiceBean {

        @Test
        void whenUserDoesNotProvideAnArgumentResolverServiceTheDefaultIsUsed() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run(
                    context -> {
                        assertThat(context).hasSingleBean(ArgumentResolverService.class);
                        assertThat(context.getBean(ArgumentResolverService.class)).isInstanceOf(DelegatingArgumentResolverService.class);
                    }
                );
        }

        @Test
        void coreArgumentResolversAreNotIncludedIfCustomArgumentResolverServiceProvided() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithArgumentResolverServiceDefined.class)
                .run(
                    context -> {
                        assertThat(context).hasSingleBean(ArgumentResolverService.class);
                        assertThat(context.getBean(ArgumentResolverService.class))
                            .isSameAs(context.getBean(UserConfigurationWithArgumentResolverServiceDefined.class).argumentResolverService());
                        assertThat(context).doesNotHaveBean(ArgumentResolver.class);
                    }
                );
        }

        @Test
        void allCoreArgumentResolversAreIncludedInDelegatingArgumentResolverServiceIfNoCustomArgumentResolverServiceProvided() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run(
                    context -> {
                        final Collection<ArgumentResolver> argumentResolvers = context.getBeansOfType(ArgumentResolver.class).values();
                        final DelegatingArgumentResolverService argumentResolverService = (DelegatingArgumentResolverService) context.getBean(
                            ArgumentResolverService.class
                        );
                        final Field argumentResolversField = DelegatingArgumentResolverService.class.getDeclaredField("argumentResolvers");
                        argumentResolversField.setAccessible(true);
                        assertThat(((List<ArgumentResolver>) argumentResolversField.get(argumentResolverService)))
                            .containsExactlyElementsOf(argumentResolvers);
                    }
                );
        }

        @Test
        void allCoreArgumentResolversAreIncludedInContextIfNoCustomArgumentResolverServiceProvided() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run(
                    context -> {
                        final Collection<Class<? extends ArgumentResolver>> argumentResolvers = context
                            .getBeansOfType(ArgumentResolver.class)
                            .values()
                            .stream()
                            .map(ArgumentResolver::getClass)
                            .collect(toSet());

                        assertThat(argumentResolvers)
                            .containsExactlyInAnyOrder(
                                PayloadArgumentResolver.class,
                                MessageIdArgumentResolver.class,
                                MessageAttributeArgumentResolver.class,
                                MessageSystemAttributeArgumentResolver.class,
                                MessageArgumentResolver.class
                            );
                    }
                );
        }

        @Test
        void userDefinedArgumentResolversAreIncludedInDefaultDelegatingArgumentResolverService() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithCustomArgumentResolver.class)
                .run(
                    context -> {
                        final Collection<ArgumentResolver> argumentResolvers = context.getBeansOfType(ArgumentResolver.class).values();
                        final DelegatingArgumentResolverService argumentResolverService = (DelegatingArgumentResolverService) context.getBean(
                            ArgumentResolverService.class
                        );
                        final Field argumentResolversField = DelegatingArgumentResolverService.class.getDeclaredField("argumentResolvers");
                        argumentResolversField.setAccessible(true);
                        assertThat(((List<ArgumentResolver>) argumentResolversField.get(argumentResolverService)))
                            .containsExactlyElementsOf(argumentResolvers);
                        assertThat(argumentResolvers).hasSize(6);
                    }
                );
        }
    }

    @Nested
    class MessageListenerContainerCoordinatorBean {

        @Test
        void defaultMessageListenerContainerCoordinatorIsUsedIfUserContextDoesNotDefineItsOwn() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run(
                    context -> {
                        assertThat(context).hasSingleBean(MessageListenerContainerCoordinator.class);
                        assertThat(context.getBean(MessageListenerContainerCoordinator.class))
                            .isInstanceOf(DefaultMessageListenerContainerCoordinator.class);
                    }
                );
        }

        @Test
        void defaultCoordinatorPropertiesWillAutoStartAllContainers() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run(
                    context -> {
                        final DefaultMessageListenerContainerCoordinatorProperties properties = context.getBean(
                            DefaultMessageListenerContainerCoordinatorProperties.class
                        );
                        assertThat(properties.isAutoStartContainersEnabled()).isTrue();
                    }
                );
        }

        @Test
        void customDefaultCoordinatorPropertiesWillOverrideDefault() {
            final DefaultMessageListenerContainerCoordinatorProperties customProperties = mock(
                DefaultMessageListenerContainerCoordinatorProperties.class
            );
            when(customProperties.isAutoStartContainersEnabled()).thenReturn(false);
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .withBean(DefaultMessageListenerContainerCoordinatorProperties.class, () -> customProperties)
                .run(
                    context -> {
                        final DefaultMessageListenerContainerCoordinatorProperties properties = context.getBean(
                            DefaultMessageListenerContainerCoordinatorProperties.class
                        );
                        assertThat(properties).isSameAs(customProperties);
                        assertThat(context.getBean(DefaultMessageListenerContainerCoordinator.class).isAutoStartup()).isFalse();
                    }
                );
        }

        @Test
        void allCoreDefinedMessageListenerContainerFactoriesAreIncludedInContext() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run(
                    context -> {
                        final Collection<Class<? extends MessageListenerContainerFactory>> MessageListenerContainerFactoryClasses = context
                            .getBeansOfType(MessageListenerContainerFactory.class)
                            .values()
                            .stream()
                            .map(MessageListenerContainerFactory::getClass)
                            .collect(toSet());

                        assertThat(MessageListenerContainerFactoryClasses)
                            .containsExactlyInAnyOrder(
                                BasicMessageListenerContainerFactory.class,
                                PrefetchingMessageListenerContainerFactory.class,
                                FifoMessageListenerContainerFactory.class
                            );
                    }
                );
        }

        @Test
        void allDefinedMessageListenerContainerFactoriesAreIncludedInDefaultMessageListenerContainerCoordinator() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run(
                    context -> {
                        final Collection<MessageListenerContainerFactory> messageListenerContainerFactories = context
                            .getBeansOfType(MessageListenerContainerFactory.class)
                            .values();
                        final DefaultMessageListenerContainerCoordinator service = (DefaultMessageListenerContainerCoordinator) context.getBean(
                            MessageListenerContainerCoordinator.class
                        );
                        final Field argumentResolversField =
                            DefaultMessageListenerContainerCoordinator.class.getDeclaredField("messageListenerContainerFactories");
                        argumentResolversField.setAccessible(true);
                        assertThat(((List<MessageListenerContainerFactory>) argumentResolversField.get(service)))
                            .containsExactlyElementsOf(messageListenerContainerFactories);
                    }
                );
        }

        @Test
        void userDefinedMessageListenerContainerFactoryIsIncludedInDefaultMessageListenerContainerCoordinator() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithCustomMessageListenerContainerFactory.class)
                .run(
                    context -> {
                        final Collection<MessageListenerContainerFactory> messageListenerContainerFactories = context
                            .getBeansOfType(MessageListenerContainerFactory.class)
                            .values();
                        final DefaultMessageListenerContainerCoordinator service = (DefaultMessageListenerContainerCoordinator) context.getBean(
                            MessageListenerContainerCoordinator.class
                        );
                        final Field argumentResolversField =
                            DefaultMessageListenerContainerCoordinator.class.getDeclaredField("messageListenerContainerFactories");
                        argumentResolversField.setAccessible(true);
                        assertThat(((List<MessageListenerContainerFactory>) argumentResolversField.get(service)))
                            .containsExactlyElementsOf(messageListenerContainerFactories);
                        assertThat(messageListenerContainerFactories).hasSize(4);
                    }
                );
        }

        @Test
        void userDefinedMessageListenerContainerCoordinatorWillNotUseDefaultService() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithCustomMessageListenerContainerCoordinator.class)
                .run(
                    context -> {
                        assertThat(context).hasSingleBean(MessageListenerContainerCoordinator.class);
                        assertThat(context.getBean(MessageListenerContainerCoordinator.class))
                            .isSameAs(
                                context
                                    .getBean(UserConfigurationWithCustomMessageListenerContainerCoordinator.class)
                                    .customMessageListenerContainerCoordinator()
                            );
                    }
                );
        }

        @Test
        void userDefinedMessageListenerContainerCoordinatorWillResultInNoCoreMessageListenerContainerFactoriesInContext() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithCustomMessageListenerContainerCoordinator.class)
                .run(context -> assertThat(context).doesNotHaveBean(MessageListenerContainerFactory.class));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class MessageProcessingDecoratorFactories {
        @Mock
        MessageProcessingDecoratorFactory<MessageProcessingDecorator> decoratorFactory;

        @Mock
        MessageProcessingDecorator decorator;

        @Test
        void willIncludeAnyMessageProcessingDecoratorFactories() {
            when(decoratorFactory.buildDecorator(any(), any(), any(), any(), any())).thenReturn(Optional.of(decorator));
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .withBean(MessageProcessingDecoratorFactory.class, () -> decoratorFactory)
                .run(
                    context -> {
                        final SqsAsyncClient sqsAsyncClient = context.getBean(SqsAsyncClient.class);
                        final DecoratingMessageProcessorFactory processorFactory = context.getBean(DecoratingMessageProcessorFactory.class);
                        final MessageProcessor processor = processorFactory.decorateMessageProcessor(
                            sqsAsyncClient,
                            "id",
                            QueueProperties.builder().queueUrl("url").build(),
                            new Object(),
                            Object.class.getMethod("toString"),
                            (message, resolveMessageCallback) -> CompletableFuture.completedFuture(null)
                        );

                        processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

                        verify(decorator).onPreMessageProcessing(any(), any());
                    }
                );
        }

        @Test
        void willIncludeGlobalMessageProcessingDecorators() {
            when(decoratorFactory.buildDecorator(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .withBean(MessageProcessingDecorator.class, () -> decorator)
                .withBean(MessageProcessingDecoratorFactory.class, () -> decoratorFactory)
                .run(
                    context -> {
                        final SqsAsyncClient sqsAsyncClient = context.getBean(SqsAsyncClient.class);
                        final DecoratingMessageProcessorFactory processorFactory = context.getBean(DecoratingMessageProcessorFactory.class);
                        final MessageProcessor processor = processorFactory.decorateMessageProcessor(
                            sqsAsyncClient,
                            "id",
                            QueueProperties.builder().queueUrl("url").build(),
                            new Object(),
                            Object.class.getMethod("toString"),
                            (message, resolveMessageCallback) -> CompletableFuture.completedFuture(null)
                        );

                        processor.processMessage(Message.builder().build(), () -> CompletableFuture.completedFuture(null));

                        verify(decorator).onPreMessageProcessing(any(), any());
                    }
                );
        }

        @Test
        void willNotWrapProcessorIfNoMessageProcessingDecoratorsOrDecoratorFactories() {
            final MessageProcessor delegate = mock(MessageProcessor.class);
            when(decoratorFactory.buildDecorator(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .withBean(MessageProcessingDecoratorFactory.class, () -> decoratorFactory)
                .run(
                    context -> {
                        final SqsAsyncClient sqsAsyncClient = context.getBean(SqsAsyncClient.class);
                        final DecoratingMessageProcessorFactory processorFactory = context.getBean(DecoratingMessageProcessorFactory.class);
                        final MessageProcessor processor = processorFactory.decorateMessageProcessor(
                            sqsAsyncClient,
                            "id",
                            QueueProperties.builder().queueUrl("url").build(),
                            new Object(),
                            Object.class.getMethod("toString"),
                            delegate
                        );

                        assertThat(processor).isSameAs(delegate);
                    }
                );
        }

        @Test
        void canProvideOwnDecoratingMessageProcessingFactory() {
            final DecoratingMessageProcessorFactory decoratingMessageProcessorFactory = mock(DecoratingMessageProcessorFactory.class);
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .withBean(DecoratingMessageProcessorFactory.class, () -> decoratingMessageProcessorFactory)
                .run(
                    context -> {
                        final DecoratingMessageProcessorFactory processorFactory = context.getBean(DecoratingMessageProcessorFactory.class);

                        assertThat(processorFactory).isSameAs(decoratingMessageProcessorFactory);
                    }
                );
        }
    }

    @Nested
    class CoreAnnotationProcessors {

        @Test
        void prefetchingQueueListenerCanProvideCustomLogic() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .withBean(CustomPrefetchingQueueListenerParser.class)
                .run(
                    context -> {
                        final PrefetchingQueueListenerParser parser = context.getBean(PrefetchingQueueListenerParser.class);
                        assertThat(parser).isInstanceOf(CustomPrefetchingQueueListenerParser.class);

                        final PrefetchingQueueListener annotation = Assertions.assertDoesNotThrow(
                            () ->
                                CoreAnnotationProcessors.class.getMethod("prefetchingListener")
                                    .getAnnotation(PrefetchingQueueListener.class)
                        );

                        final PrefetchingMessageListenerContainerProperties properties = parser.parse(annotation);
                        assertThat(properties.concurrencyLevel()).isEqualTo(100);
                    }
                );
        }

        @Test
        void fifoQueueListenerCanProvideCustomLogic() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .withBean(CustomFifoQueueListenerParser.class)
                .run(
                    context -> {
                        final FifoQueueListenerParser parser = context.getBean(FifoQueueListenerParser.class);
                        assertThat(parser).isInstanceOf(CustomFifoQueueListenerParser.class);

                        final FifoQueueListener annotation = Assertions.assertDoesNotThrow(
                            () -> CoreAnnotationProcessors.class.getMethod("fifoListener").getAnnotation(FifoQueueListener.class)
                        );

                        final FifoMessageListenerContainerProperties properties = parser.parse(annotation);
                        assertThat(properties.concurrencyLevel()).isEqualTo(200);
                    }
                );
        }

        @Test
        void queueListenerCanProvideCustomLogic() {
            contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .withBean(CustomQueueListenerParser.class)
                .run(
                    context -> {
                        final QueueListenerParser parser = context.getBean(QueueListenerParser.class);
                        assertThat(parser).isInstanceOf(CustomQueueListenerParser.class);

                        final QueueListener annotation = Assertions.assertDoesNotThrow(
                            () -> CoreAnnotationProcessors.class.getMethod("queueListener").getAnnotation(QueueListener.class)
                        );

                        final BatchingMessageListenerContainerProperties properties = parser.parse(annotation);
                        assertThat(properties.concurrencyLevel()).isEqualTo(300);
                    }
                );
        }

        @PrefetchingQueueListener("url")
        public void prefetchingListener() {}

        @FifoQueueListener("url")
        public void fifoListener() {}

        @QueueListener("url")
        public void queueListener() {}
    }

    public static class CustomPrefetchingQueueListenerParser extends PrefetchingQueueListenerParser {

        public CustomPrefetchingQueueListenerParser(Environment environment) {
            super(environment);
        }

        @Override
        protected Supplier<Integer> concurrencySupplier(PrefetchingQueueListener annotation) {
            return () -> 100;
        }
    }

    public static class CustomFifoQueueListenerParser extends FifoQueueListenerParser {

        public CustomFifoQueueListenerParser(Environment environment) {
            super(environment);
        }

        @Override
        protected Supplier<Integer> concurrencySupplier(FifoQueueListener annotation) {
            return () -> 200;
        }
    }

    public static class CustomQueueListenerParser extends QueueListenerParser {

        public CustomQueueListenerParser(Environment environment) {
            super(environment);
        }

        @Override
        protected Supplier<Integer> concurrencySupplier(QueueListener annotation) {
            return () -> 300;
        }
    }

    @Configuration
    static class UserConfigurationWithSqsClient {

        @Bean
        SqsAsyncClient userDefinedSqsAsyncClient() {
            return mock(SqsAsyncClient.class);
        }
    }

    @Import(UserConfigurationWithSqsClient.class)
    @Configuration
    static class UserConfigurationWithPayloadArgumentResolverDefined {

        @Bean
        PayloadArgumentResolver payloadArgumentResolver() {
            return mock(PayloadArgumentResolver.class);
        }
    }

    @Import(UserConfigurationWithSqsClient.class)
    @Configuration
    static class UserConfigurationWithMessageAttributeArgumentResolverDefined {

        @Bean
        MessageAttributeArgumentResolver messageAttributeArgumentResolver() {
            return mock(MessageAttributeArgumentResolver.class);
        }
    }

    @Import(UserConfigurationWithSqsClient.class)
    @Configuration
    static class UserConfigurationWithArgumentResolverServiceDefined {

        @Bean
        ArgumentResolverService argumentResolverService() {
            return mock(ArgumentResolverService.class);
        }
    }

    @Import(UserConfigurationWithSqsClient.class)
    @Configuration
    static class UserConfigurationWithCustomArgumentResolver {

        @Bean
        ArgumentResolver<?> customArgumentResolver() {
            return mock(ArgumentResolver.class);
        }
    }

    @Import(UserConfigurationWithSqsClient.class)
    @Configuration
    static class UserConfigurationWithCustomMessageListenerContainerCoordinator {

        @Bean
        MessageListenerContainerCoordinator customMessageListenerContainerCoordinator() {
            return mock(MessageListenerContainerCoordinator.class);
        }
    }

    @Import(UserConfigurationWithSqsClient.class)
    @Configuration
    static class UserConfigurationWithCustomMessageListenerContainerFactory {

        @Bean
        MessageListenerContainerFactory customMessageListenerContainerFactory() {
            return mock(MessageListenerContainerFactory.class);
        }
    }

    @Configuration
    static class UserConfigurationWithSqsClientProvider {

        @Bean
        SqsAsyncClientProvider userDefinedSqsAsyncClientProvider() {
            return mock(SqsAsyncClientProvider.class);
        }
    }
}
