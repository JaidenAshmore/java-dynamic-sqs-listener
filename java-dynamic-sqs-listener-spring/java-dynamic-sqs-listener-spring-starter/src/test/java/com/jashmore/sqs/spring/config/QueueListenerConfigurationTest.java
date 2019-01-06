package com.jashmore.sqs.spring.config;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.jashmore.sqs.argument.ArgumentResolver;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DelegatingArgumentResolverService;
import com.jashmore.sqs.argument.acknowledge.AcknowledgeArgumentResolver;
import com.jashmore.sqs.argument.messageid.MessageIdArgumentResolver;
import com.jashmore.sqs.argument.payload.PayloadArgumentResolver;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.argument.visibility.VisibilityExtenderArgumentResolver;
import com.jashmore.sqs.spring.QueueWrapper;
import com.jashmore.sqs.spring.container.DefaultQueueContainerService;
import com.jashmore.sqs.spring.container.QueueContainerService;
import com.jashmore.sqs.spring.container.basic.QueueListenerWrapper;
import com.jashmore.sqs.spring.container.custom.CustomQueueWrapper;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
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

@SuppressWarnings("unchecked")
public class QueueListenerConfigurationTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(QueueListenerConfiguration.class));

    @Test
    public void whenUserDoesNotProvidedSqsAsyncClientTheDefaultIsUsed() {
        this.contextRunner
                .withSystemProperties("aws.region:localstack")
                .run((context) -> {
                    assertThat(context).hasSingleBean(SqsAsyncClient.class);
                    assertThat(context.getBean(SqsAsyncClient.class)).isSameAs(
                            context.getBean(QueueListenerConfiguration.class).sqsAsyncClient());
                });
    }

    @Test
    public void whenUserProvidesSqsAsyncClientTheDefaultIsNotUsed() {
        this.contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(SqsAsyncClient.class);
                    assertThat(context.getBean(SqsAsyncClient.class)).isSameAs(
                            context.getBean(UserConfigurationWithSqsClient.class).userDefinedSqsAsyncClient());
                });
    }

    @Test
    public void whenUserDoesNotProvideAPayloadMapperTheDefaultIsUsed() {
        this.contextRunner
                .withSystemProperties("aws.region:localstack")
                .run((context) -> {
                    assertThat(context).hasSingleBean(PayloadMapper.class);
                    assertThat(context.getBean(PayloadMapper.class)).isSameAs(
                            context.getBean(QueueListenerConfiguration.ArgumentResolutionConfiguration.CoreArgumentResolversConfiguration.class)
                                    .payloadMapper());
                });
    }

    @Test
    public void whenUserProvidesAPayloadMapperTheDefaultIsNotUsed() {
        this.contextRunner
                .withUserConfiguration(UserConfigurationWithPayloadMapperDefined.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(PayloadMapper.class);
                    assertThat(context.getBean(PayloadMapper.class)).isSameAs(
                            context.getBean(UserConfigurationWithPayloadMapperDefined.class).payloadMapper());
                });
    }

    @Test
    public void whenUserDoesNotProvideAnArgumentResolverServiceTheDefaultIsUsed() {
        this.contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(ArgumentResolverService.class);
                    assertThat(context.getBean(ArgumentResolverService.class)).isInstanceOf(DelegatingArgumentResolverService.class);
                });
    }

    @Test
    public void coreArgumentResolversAreNotIncludedIfCustomArgumentResolverServiceProvided() {
        this.contextRunner
                .withUserConfiguration(UserConfigurationWithArgumentResolverServiceDefined.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(ArgumentResolverService.class);
                    assertThat(context.getBean(ArgumentResolverService.class))
                            .isSameAs(context.getBean(UserConfigurationWithArgumentResolverServiceDefined.class).argumentResolverService());
                    assertThat(context).doesNotHaveBean(ArgumentResolver.class);
                });
    }

    @Test
    public void allCoreArgumentResolversAreIncludedInDelegatingArgumentResolverServiceIfNoCustomArgumentResolverServiceProvided() {
        this.contextRunner
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
    public void allCoreArgumentResolversAreIncludedInContextIfNoCustomArgumentResolverServiceProvided() {
        this.contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run((context) -> {
                    final Collection<Class<? extends ArgumentResolver>> argumentResolvers = context.getBeansOfType(ArgumentResolver.class).values().stream()
                            .map(ArgumentResolver::getClass)
                            .collect(toSet());

                    assertThat(argumentResolvers).containsExactlyInAnyOrder(
                            PayloadArgumentResolver.class, MessageIdArgumentResolver.class,
                            AcknowledgeArgumentResolver.class, VisibilityExtenderArgumentResolver.class
                    );
                });
    }

    @Test
    public void userDefinedArgumentResolversAreIncludedInDefaultDelegatingArgumentResolverService() {
        this.contextRunner
                .withUserConfiguration(UserConfigurationWithCustomArgumentResolver.class)
                .run((context) -> {
                    final Collection<ArgumentResolver> argumentResolvers = context.getBeansOfType(ArgumentResolver.class).values();
                    final DelegatingArgumentResolverService argumentResolverService
                            = (DelegatingArgumentResolverService) context.getBean(ArgumentResolverService.class);
                    final Field argumentResolversField = DelegatingArgumentResolverService.class.getDeclaredField("argumentResolvers");
                    argumentResolversField.setAccessible(true);
                    assertThat(((Set<ArgumentResolver>) argumentResolversField.get(argumentResolverService)))
                            .containsExactlyElementsOf(argumentResolvers);
                    assertThat(argumentResolvers).hasSize(5);
                });
    }

    @Test
    public void defaultQueueContainerServiceIsUsedIfUserContextDoesNotDefineItsOwn() {
        this.contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(QueueContainerService.class);
                    assertThat(context.getBean(QueueContainerService.class)).isInstanceOf(DefaultQueueContainerService.class);
                });
    }

    @Test
    public void allCoreDefinedQueueWrappersAreIncludedInContext() {
        this.contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run((context) -> {
                    final Collection<Class<? extends QueueWrapper>> queueWrapperClasses = context.getBeansOfType(QueueWrapper.class).values().stream()
                            .map(QueueWrapper::getClass)
                            .collect(toSet());

                    assertThat(queueWrapperClasses).containsExactlyInAnyOrder(QueueListenerWrapper.class, CustomQueueWrapper.class);
                });
    }

    @Test
    public void allDefinedQueueWrappersAreIncludedInDefaultQueueContainerService() {
        this.contextRunner
                .withUserConfiguration(UserConfigurationWithSqsClient.class)
                .run((context) -> {
                    final Collection<QueueWrapper> queueWrappers = context.getBeansOfType(QueueWrapper.class).values();
                    final DefaultQueueContainerService service = (DefaultQueueContainerService) context.getBean(QueueContainerService.class);
                    final Field argumentResolversField = DefaultQueueContainerService.class.getDeclaredField("queueWrappers");
                    argumentResolversField.setAccessible(true);
                    assertThat(((List<QueueWrapper>) argumentResolversField.get(service)))
                            .containsExactlyElementsOf(queueWrappers);
                });
    }

    @Test
    public void userDefinedQueueWrapperIsIncludedInDefaultQueueContainerService() {
        this.contextRunner
                .withUserConfiguration(UserConfigurationWithCustomQueueWrapper.class)
                .run((context) -> {
                    final Collection<QueueWrapper> queueWrappers = context.getBeansOfType(QueueWrapper.class).values();
                    final DefaultQueueContainerService service = (DefaultQueueContainerService) context.getBean(QueueContainerService.class);
                    final Field argumentResolversField = DefaultQueueContainerService.class.getDeclaredField("queueWrappers");
                    argumentResolversField.setAccessible(true);
                    assertThat(((List<QueueWrapper>) argumentResolversField.get(service)))
                            .containsExactlyElementsOf(queueWrappers);
                    assertThat(queueWrappers).hasSize(3);
                });
    }

    @Test
    public void userDefinedQueueContainerServiceWillNotUseDefaultService() {
        this.contextRunner
                .withUserConfiguration(UserConfigurationWithCustomQueueContainerService.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(QueueContainerService.class);
                    assertThat(context.getBean(QueueContainerService.class))
                            .isSameAs(context.getBean(UserConfigurationWithCustomQueueContainerService.class).customQueueContainerService());
                });
    }

    @Test
    public void userDefinedQueueContainerServiceWillResultInNoCoreQueueWrappersInContext() {
        this.contextRunner
                .withUserConfiguration(UserConfigurationWithCustomQueueContainerService.class)
                .run((context) -> assertThat(context).doesNotHaveBean(QueueWrapper.class));
    }

    @Configuration
    static class UserConfigurationWithSqsClient {
        @Bean
        public SqsAsyncClient userDefinedSqsAsyncClient() {
            return mock(SqsAsyncClient.class);
        }
    }

    @Import(UserConfigurationWithSqsClient.class)
    @Configuration
    static class UserConfigurationWithPayloadMapperDefined {
        @Bean
        public PayloadMapper payloadMapper() {
            return mock(PayloadMapper.class);
        }
    }

    @Import(UserConfigurationWithSqsClient.class)
    @Configuration
    static class UserConfigurationWithArgumentResolverServiceDefined {
        @Bean
        public ArgumentResolverService argumentResolverService() {
            return mock(ArgumentResolverService.class);
        }
    }

    @Import(UserConfigurationWithSqsClient.class)
    @Configuration
    static class UserConfigurationWithCustomArgumentResolver {
        @Bean
        public ArgumentResolver customArgumentResolver() {
            return mock(ArgumentResolver.class);
        }
    }

    @Import(UserConfigurationWithSqsClient.class)
    @Configuration
    static class UserConfigurationWithCustomQueueContainerService {
        @Bean
        public QueueContainerService customQueueContainerService() {
            return mock(QueueContainerService.class);
        }
    }

    @Import(UserConfigurationWithSqsClient.class)
    @Configuration
    static class UserConfigurationWithCustomQueueWrapper {
        @Bean
        public QueueWrapper customQueueWrapper() {
            return mock(QueueWrapper.class);
        }
    }
}