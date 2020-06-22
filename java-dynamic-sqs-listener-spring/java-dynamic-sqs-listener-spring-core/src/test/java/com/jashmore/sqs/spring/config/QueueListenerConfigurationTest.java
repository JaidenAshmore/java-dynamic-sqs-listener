package com.jashmore.sqs.spring.config;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DelegatingArgumentResolverService;
import com.jashmore.sqs.argument.attribute.MessageAttributeArgumentResolver;
import com.jashmore.sqs.argument.attribute.MessageSystemAttributeArgumentResolver;
import com.jashmore.sqs.argument.message.MessageArgumentResolver;
import com.jashmore.sqs.argument.messageid.MessageIdArgumentResolver;
import com.jashmore.sqs.argument.payload.PayloadArgumentResolver;
import com.jashmore.sqs.spring.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.container.DefaultMessageListenerContainerCoordinator;
import com.jashmore.sqs.spring.container.MessageListenerContainerCoordinator;
import com.jashmore.sqs.spring.container.MessageListenerContainerFactory;
import com.jashmore.sqs.spring.container.basic.BasicMessageListenerContainerFactory;
import com.jashmore.sqs.spring.container.prefetch.PrefetchingMessageListenerContainerFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@SuppressWarnings( {"unchecked", "rawtypes"})
class QueueListenerConfigurationTest {
    private static final ObjectMapper USER_OBJECT_MAPPER = new ObjectMapper();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(QueueListenerConfiguration.class));

    @Nested
    class SqsAsyncClientBean {
        @Test
        void whenUserDoesNotProvidedSqsAsyncClientTheDefaultIsUsed() {
            contextRunner
                    .withSystemProperties("aws.region:localstack")
                    .run((context) -> {
                        assertThat(context).hasSingleBean(SqsAsyncClient.class);
                        assertThat(context.getBean(SqsAsyncClient.class)).isSameAs(
                                context.getBean(QueueListenerConfiguration.class).sqsAsyncClient());
                    });
        }

        @Test
        void whenUserProvidesSqsAsyncClientTheDefaultIsNotUsed() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithSqsClient.class)
                    .run((context) -> {
                        assertThat(context).hasSingleBean(SqsAsyncClient.class);
                        assertThat(context.getBean(SqsAsyncClient.class)).isSameAs(
                                context.getBean(UserConfigurationWithSqsClient.class).userDefinedSqsAsyncClient());
                    });
        }
    }

    @Nested
    class SqsAsyncClientProviderBean {


        @Test
        void whenNoSqsAsyncClientProviderADefaultImplementationIsCreated() {
            contextRunner
                    .withSystemProperties("aws.region:localstack")
                    .run((context) -> {
                        assertThat(context).hasSingleBean(SqsAsyncClientProvider.class);
                        assertThat(context.getBean(SqsAsyncClientProvider.class)).isSameAs(
                                context.getBean(QueueListenerConfiguration.class).sqsAsyncClientProvider(null));
                    });
        }

        @Test
        void whenNoCustomSqsAsyncClientProviderAndSqsAsyncClientDefaultSqsAsyncClientIsTheSqsAsyncClientProvidersDefault() {
            contextRunner
                    .withSystemProperties("aws.region:localstack")
                    .run((context) -> {
                        final SqsAsyncClientProvider sqsAsyncClientProvider = context.getBean(SqsAsyncClientProvider.class);
                        final SqsAsyncClient expectedDefault = context.getBean(SqsAsyncClient.class);
                        assertThat(sqsAsyncClientProvider.getDefaultClient()).contains(expectedDefault);
                    });
        }

        @Test
        void whenCustomSqsAsyncClientProvidedTheDefaultSqsAsyncClientProviderUsesThisAsTheDefault() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithSqsClient.class)
                    .run((context) -> {
                        final SqsAsyncClientProvider sqsAsyncClientProvider = context.getBean(SqsAsyncClientProvider.class);
                        final SqsAsyncClient userDefinedSqsAsyncClient = context.getBean(UserConfigurationWithSqsClient.class).userDefinedSqsAsyncClient();
                        assertThat(sqsAsyncClientProvider.getDefaultClient()).contains(userDefinedSqsAsyncClient);
                    });
        }

        @Test
        void whenCustomSqsAsyncClientProviderProvidedNoSqsAsyncClientBeanIsBuilt() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithSqsClientProvider.class)
                    .run((context) -> assertThat(context).doesNotHaveBean(SqsAsyncClient.class));
        }

        @Test
        void whenCustomSqsAsyncClientProviderThatIsUsedInsteadOfTheDefault() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithSqsClientProvider.class)
                    .run((context) -> assertThat(context.getBean(SqsAsyncClientProvider.class))
                            .isSameAs(context.getBean(UserConfigurationWithSqsClientProvider.class).userDefinedSqsAsyncClientProvider()));
        }
    }

    @Nested
    class ObjectMapperBean {
        @Test
        void userConfigurationWithNoObjectMapperWillProvideOwnForArgumentResolvers() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithSqsClient.class)
                    .run((context) -> {
                        final ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
                        assertThat(objectMapper).isNotNull();
                    });
        }

        @Test
        void customObjectMapperWillBeAbleToObtainThatObjectMapperWhenRequired() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithCustomObjectMapper.class)
                    .run((context) -> {
                        final ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
                        assertThat(objectMapper).isEqualTo(USER_OBJECT_MAPPER);
                    });
        }
    }

    @Nested
    class PayloadArgumentResolverBean {
        @Test
        void whenUserDoesNotProvideAPayloadArgumentResolverTheDefaultIsUsed() {
            contextRunner
                    .withSystemProperties("aws.region:localstack")
                    .run((context) -> {
                        assertThat(context).hasSingleBean(PayloadArgumentResolver.class);
                        assertThat(context.getBean(PayloadArgumentResolver.class)).isSameAs(
                                context.getBean(QueueListenerConfiguration.ArgumentResolutionConfiguration.CoreArgumentResolversConfiguration.class)
                                        .payloadArgumentResolver(new ObjectMapper()));
                    });
        }

        @Test
        void whenUserProvidesAPayloadArgumentResolverTheDefaultIsNotUsed() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithPayloadArgumentResolverDefined.class)
                    .run((context) -> {
                        assertThat(context).hasSingleBean(PayloadArgumentResolver.class);
                        assertThat(context.getBean(PayloadArgumentResolver.class)).isSameAs(
                                context.getBean(UserConfigurationWithPayloadArgumentResolverDefined.class).payloadArgumentResolver());
                    });
        }
    }

    @Nested
    class MessageAttributeArgumentResolverBean {
        @Test
        void whenNoUserProvidesAMessageAttributeArgumentResolverTheDefaultIsUsed() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithMessageAttributeArgumentResolverDefined.class)
                    .run((context) -> {
                        assertThat(context).hasSingleBean(MessageAttributeArgumentResolver.class);
                        assertThat(context.getBean(MessageAttributeArgumentResolver.class)).isSameAs(
                                context.getBean(UserConfigurationWithMessageAttributeArgumentResolverDefined.class).messageAttributeArgumentResolver());
                    });
        }

        @Test
        void whenUserProvidesAMessageAttributeArgumentResolverTheDefaultIsNotUsed() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithMessageAttributeArgumentResolverDefined.class)
                    .run((context) -> {
                        assertThat(context).hasSingleBean(MessageAttributeArgumentResolver.class);
                        assertThat(context.getBean(MessageAttributeArgumentResolver.class)).isSameAs(
                                context.getBean(UserConfigurationWithMessageAttributeArgumentResolverDefined.class).messageAttributeArgumentResolver());
                    });
        }
    }

    @Nested
    class ArgumentResolverServiceBean {
        @Test
        void whenUserDoesNotProvideAnArgumentResolverServiceTheDefaultIsUsed() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithSqsClient.class)
                    .run((context) -> {
                        assertThat(context).hasSingleBean(ArgumentResolverService.class);
                        assertThat(context.getBean(ArgumentResolverService.class)).isInstanceOf(DelegatingArgumentResolverService.class);
                    });
        }

        @Test
        void coreArgumentResolversAreNotIncludedIfCustomArgumentResolverServiceProvided() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithArgumentResolverServiceDefined.class)
                    .run((context) -> {
                        assertThat(context).hasSingleBean(ArgumentResolverService.class);
                        assertThat(context.getBean(ArgumentResolverService.class))
                                .isSameAs(context.getBean(UserConfigurationWithArgumentResolverServiceDefined.class).argumentResolverService());
                        assertThat(context).doesNotHaveBean(ArgumentResolver.class);
                    });
        }

        @Test
        void allCoreArgumentResolversAreIncludedInDelegatingArgumentResolverServiceIfNoCustomArgumentResolverServiceProvided() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithSqsClient.class)
                    .run((context) -> {
                        final Collection<ArgumentResolver> argumentResolvers = context.getBeansOfType(ArgumentResolver.class).values();
                        final DelegatingArgumentResolverService argumentResolverService
                                = (DelegatingArgumentResolverService) context.getBean(ArgumentResolverService.class);
                        final Field argumentResolversField = DelegatingArgumentResolverService.class.getDeclaredField("argumentResolvers");
                        argumentResolversField.setAccessible(true);
                        assertThat(((Set<ArgumentResolver>) argumentResolversField.get(argumentResolverService)))
                                .containsExactlyElementsOf(argumentResolvers);
                    });
        }

        @Test
        void allCoreArgumentResolversAreIncludedInContextIfNoCustomArgumentResolverServiceProvided() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithSqsClient.class)
                    .run((context) -> {
                        final Collection<Class<? extends ArgumentResolver>> argumentResolvers = context.getBeansOfType(ArgumentResolver.class).values().stream()
                                .map(ArgumentResolver::getClass)
                                .collect(toSet());

                        assertThat(argumentResolvers).containsExactlyInAnyOrder(
                                PayloadArgumentResolver.class, MessageIdArgumentResolver.class,
                                MessageAttributeArgumentResolver.class, MessageSystemAttributeArgumentResolver.class, MessageArgumentResolver.class
                        );
                    });
        }

        @Test
        void userDefinedArgumentResolversAreIncludedInDefaultDelegatingArgumentResolverService() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithCustomArgumentResolver.class)
                    .run((context) -> {
                        final Collection<ArgumentResolver> argumentResolvers = context.getBeansOfType(ArgumentResolver.class).values();
                        final DelegatingArgumentResolverService argumentResolverService
                                = (DelegatingArgumentResolverService) context.getBean(ArgumentResolverService.class);
                        final Field argumentResolversField = DelegatingArgumentResolverService.class.getDeclaredField("argumentResolvers");
                        argumentResolversField.setAccessible(true);
                        assertThat(((Set<ArgumentResolver>) argumentResolversField.get(argumentResolverService)))
                                .containsExactlyElementsOf(argumentResolvers);
                        assertThat(argumentResolvers).hasSize(6);
                    });
        }
    }

    @Nested
    class MessageListenerContainerCoordinatorBean {
        @Test
        void defaultMessageListenerContainerCoordinatorIsUsedIfUserContextDoesNotDefineItsOwn() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithSqsClient.class)
                    .run((context) -> {
                        assertThat(context).hasSingleBean(MessageListenerContainerCoordinator.class);
                        assertThat(context.getBean(MessageListenerContainerCoordinator.class)).isInstanceOf(DefaultMessageListenerContainerCoordinator.class);
                    });
        }

        @Test
        void allCoreDefinedMessageListenerContainerFactoriesAreIncludedInContext() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithSqsClient.class)
                    .run((context) -> {
                        final Collection<Class<? extends MessageListenerContainerFactory>> MessageListenerContainerFactoryClasses = context.getBeansOfType(MessageListenerContainerFactory.class).values().stream()
                                .map(MessageListenerContainerFactory::getClass)
                                .collect(toSet());

                        assertThat(MessageListenerContainerFactoryClasses).containsExactlyInAnyOrder(BasicMessageListenerContainerFactory.class, PrefetchingMessageListenerContainerFactory.class);
                    });
        }

        @Test
        void allDefinedMessageListenerContainerFactoriesAreIncludedInDefaultMessageListenerContainerCoordinator() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithSqsClient.class)
                    .run((context) -> {
                        final Collection<MessageListenerContainerFactory> messageListenerContainerFactories = context.getBeansOfType(MessageListenerContainerFactory.class).values();
                        final DefaultMessageListenerContainerCoordinator service = (DefaultMessageListenerContainerCoordinator) context.getBean(MessageListenerContainerCoordinator.class);
                        final Field argumentResolversField = DefaultMessageListenerContainerCoordinator.class.getDeclaredField("messageListenerContainerFactories");
                        argumentResolversField.setAccessible(true);
                        assertThat(((List<MessageListenerContainerFactory>) argumentResolversField.get(service)))
                                .containsExactlyElementsOf(messageListenerContainerFactories);
                    });
        }

        @Test
        void userDefinedMessageListenerContainerFactoryIsIncludedInDefaultMessageListenerContainerCoordinator() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithCustomMessageListenerContainerFactory.class)
                    .run((context) -> {
                        final Collection<MessageListenerContainerFactory> messageListenerContainerFactories = context.getBeansOfType(MessageListenerContainerFactory.class).values();
                        final DefaultMessageListenerContainerCoordinator service = (DefaultMessageListenerContainerCoordinator) context.getBean(MessageListenerContainerCoordinator.class);
                        final Field argumentResolversField = DefaultMessageListenerContainerCoordinator.class.getDeclaredField("messageListenerContainerFactories");
                        argumentResolversField.setAccessible(true);
                        assertThat(((List<MessageListenerContainerFactory>) argumentResolversField.get(service)))
                                .containsExactlyElementsOf(messageListenerContainerFactories);
                        assertThat(messageListenerContainerFactories).hasSize(3);
                    });
        }

        @Test
        void userDefinedMessageListenerContainerCoordinatorWillNotUseDefaultService() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithCustomMessageListenerContainerCoordinator.class)
                    .run((context) -> {
                        assertThat(context).hasSingleBean(MessageListenerContainerCoordinator.class);
                        assertThat(context.getBean(MessageListenerContainerCoordinator.class))
                                .isSameAs(context.getBean(UserConfigurationWithCustomMessageListenerContainerCoordinator.class).customMessageListenerContainerCoordinator());
                    });
        }

        @Test
        void userDefinedMessageListenerContainerCoordinatorWillResultInNoCoreMessageListenerContainerFactoriesInContext() {
            contextRunner
                    .withUserConfiguration(UserConfigurationWithCustomMessageListenerContainerCoordinator.class)
                    .run((context) -> assertThat(context).doesNotHaveBean(MessageListenerContainerFactory.class));
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
    static class UserConfigurationWithCustomObjectMapper {
        @Bean
        ObjectMapper myObjectMapper() {
            return USER_OBJECT_MAPPER;
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