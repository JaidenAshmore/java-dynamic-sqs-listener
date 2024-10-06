package com.jashmore.sqs.extensions.xray.decorator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.decorator.MessageProcessingContext;
import com.jashmore.sqs.extensions.xray.client.XrayWrappedSqsAsyncClient;
import com.jashmore.sqs.extensions.xray.spring.SqsListenerXrayConfiguration;
import com.jashmore.sqs.client.SqsAsyncClientProvider;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@ExtendWith(MockitoExtension.class)
class SqsListenerXrayConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(QueueListenerConfiguration.class, SqsListenerXrayConfiguration.class));

    @Mock
    private AWSXRayRecorder mockGlobalRecorder;

    private AWSXRayRecorder globalRecorder;

    @BeforeEach
    void setupMockRecorder() {
        globalRecorder = AWSXRay.getGlobalRecorder();
        AWSXRay.setGlobalRecorder(mockGlobalRecorder);
    }

    @AfterEach
    void tearDown() {
        AWSXRay.setGlobalRecorder(globalRecorder);
    }

    @Nested
    class Decorator {

        @Test
        void shouldProvideDecoratorBean() {
            contextRunner
                .withBean(SqsAsyncClient.class, () -> mock(SqsAsyncClient.class))
                .run(context -> assertThat(context).hasSingleBean(BasicXrayMessageProcessingDecorator.class));
        }

        @Test
        void willSetSegmentAsDefaultNameWhenApplicationNameNotPresent() {
            contextRunner
                .withBean(SqsAsyncClient.class, () -> mock(SqsAsyncClient.class))
                .run(context -> {
                    when(mockGlobalRecorder.beginSegment(anyString())).thenReturn(mock(Segment.class));
                    when(mockGlobalRecorder.beginSubsegment(anyString())).thenReturn(mock(Subsegment.class));
                    final BasicXrayMessageProcessingDecorator decorator = context.getBean(BasicXrayMessageProcessingDecorator.class);
                    decorator.onPreMessageProcessing(
                        MessageProcessingContext
                            .builder()
                            .attributes(new HashMap<>())
                            .listenerIdentifier("identifier")
                            .queueProperties(QueueProperties.builder().queueUrl("url").build())
                            .build(),
                        Message.builder().build()
                    );

                    verify(mockGlobalRecorder).beginSegment("service");
                });
        }

        @Test
        void willSetSegmentAsServiceNameWhenApplicationNameNotPresent() {
            contextRunner
                .withPropertyValues("spring.application.name=my-service-name")
                .withBean(SqsAsyncClient.class, () -> mock(SqsAsyncClient.class))
                .run(context -> {
                    when(mockGlobalRecorder.beginSegment(anyString())).thenReturn(mock(Segment.class));
                    when(mockGlobalRecorder.beginSubsegment(anyString())).thenReturn(mock(Subsegment.class));
                    final BasicXrayMessageProcessingDecorator decorator = context.getBean(BasicXrayMessageProcessingDecorator.class);
                    decorator.onPreMessageProcessing(
                        MessageProcessingContext
                            .builder()
                            .attributes(new HashMap<>())
                            .listenerIdentifier("identifier")
                            .queueProperties(QueueProperties.builder().queueUrl("url").build())
                            .build(),
                        Message.builder().build()
                    );

                    verify(mockGlobalRecorder).beginSegment("my-service-name");
                });
        }

        @Test
        void willUseCustomRecorderIfExplicitlySet() {
            final AWSXRayRecorder mockRecorder = mock(AWSXRayRecorder.class);
            contextRunner
                .withBean(SqsAsyncClient.class, () -> mock(SqsAsyncClient.class))
                .withBean("sqsXrayRecorder", AWSXRayRecorder.class, () -> mockRecorder)
                .run(context -> {
                    when(mockRecorder.beginSegment(anyString())).thenReturn(mock(Segment.class));
                    when(mockRecorder.beginSubsegment(anyString())).thenReturn(mock(Subsegment.class));
                    final BasicXrayMessageProcessingDecorator decorator = context.getBean(BasicXrayMessageProcessingDecorator.class);
                    decorator.onPreMessageProcessing(
                        MessageProcessingContext
                            .builder()
                            .attributes(new HashMap<>())
                            .listenerIdentifier("identifier")
                            .queueProperties(QueueProperties.builder().queueUrl("url").build())
                            .build(),
                        Message.builder().build()
                    );

                    verify(mockRecorder).beginSegment("service");
                });
        }
    }

    @Nested
    class ClientProvider {

        @Test
        void whenNoSqsAsyncClientProvidedDefaultWillBeCreated() {
            contextRunner.withSystemProperties("aws.region:localstack").run(context -> context.getBean(SqsAsyncClientProvider.class));
        }

        @Test
        void defaultProviderWrapsSqsClientWithXRayLogic() {
            final SqsAsyncClient defaultClient = mock(SqsAsyncClient.class);

            contextRunner
                .withBean(SqsAsyncClient.class, () -> defaultClient)
                .run(context -> {
                    when(mockGlobalRecorder.beginSegment(anyString())).thenReturn(mock(Segment.class));
                    final SqsAsyncClientProvider provider = context.getBean(SqsAsyncClientProvider.class);
                    final SqsAsyncClient client = provider.getDefaultClient().orElseThrow(() -> new RuntimeException("Error"));
                    assertThat(client).isInstanceOf(XrayWrappedSqsAsyncClient.class);

                    client.sendMessage(SendMessageRequest.builder().build());

                    verify(defaultClient).sendMessage(any(SendMessageRequest.class));
                });
        }

        @Test
        void willPublishMetricsWithDefaultSegmentNameIfApplicationNameMissing() {
            contextRunner
                .withBean(SqsAsyncClient.class, () -> mock(SqsAsyncClient.class))
                .run(context -> {
                    when(mockGlobalRecorder.beginSegment(anyString())).thenReturn(mock(Segment.class));
                    final SqsAsyncClient client = context
                        .getBean(SqsAsyncClientProvider.class)
                        .getDefaultClient()
                        .orElseThrow(() -> new RuntimeException("Error"));

                    client.sendMessage(SendMessageRequest.builder().build());

                    verify(mockGlobalRecorder).beginSegment("service");
                    verify(mockGlobalRecorder).endSegment();
                });
        }

        @Test
        void willPublishMetricsWithApplicationNameWhenSet() {
            contextRunner
                .withPropertyValues("spring.application.name=my-service-name")
                .withBean(SqsAsyncClient.class, () -> mock(SqsAsyncClient.class))
                .run(context -> {
                    when(mockGlobalRecorder.beginSegment(anyString())).thenReturn(mock(Segment.class));
                    final SqsAsyncClient client = context
                        .getBean(SqsAsyncClientProvider.class)
                        .getDefaultClient()
                        .orElseThrow(() -> new RuntimeException("Error"));

                    client.sendMessage(SendMessageRequest.builder().build());

                    verify(mockGlobalRecorder).beginSegment("my-service-name");
                    verify(mockGlobalRecorder).endSegment();
                });
        }

        @Test
        void shouldUseProvidedClientProviderIfOneSet() {
            final SqsAsyncClientProvider clientProvider = mock(SqsAsyncClientProvider.class);
            contextRunner
                .withBean(SqsAsyncClientProvider.class, () -> clientProvider)
                .run(context -> assertThat(context.getBean(SqsAsyncClientProvider.class)).isSameAs(clientProvider));
        }

        @Test
        void canIncludeCustomRecorderNotUsedByThisLibrary() {
            contextRunner
                .withBean(SqsAsyncClient.class, () -> mock(SqsAsyncClient.class))
                .withBean("anotherXrayRecorder", AWSXRayRecorder.class, () -> mock(AWSXRayRecorder.class))
                .run(context -> {
                    assertThat(context).hasBean("anotherXrayRecorder");
                    assertThat(context).hasBean("sqsXrayRecorder");
                    context.getBean(SqsAsyncClientProvider.class);
                });
        }

        @Test
        void canOverrideSqsXrayRecorderUsingBeanName() {
            final AWSXRayRecorder mockRecorder = mock(AWSXRayRecorder.class);
            contextRunner
                .withBean(SqsAsyncClient.class, () -> mock(SqsAsyncClient.class))
                .withBean("sqsXrayRecorder", AWSXRayRecorder.class, () -> mockRecorder)
                .run(context -> {
                    when(mockRecorder.beginSegment(anyString())).thenReturn(mock(Segment.class));
                    assertThat(context).hasSingleBean(AWSXRayRecorder.class);
                    assertThat(context.getBean("sqsXrayRecorder")).isSameAs(mockRecorder);

                    final SqsAsyncClient sqsAsyncClient = context
                        .getBean(SqsAsyncClientProvider.class)
                        .getDefaultClient()
                        .orElseThrow(() -> new RuntimeException(""));
                    sqsAsyncClient.sendMessage(SendMessageRequest.builder().build());
                    verify(mockRecorder).beginSegment(anyString());
                });
        }

        @Test
        void willUseGlobalRecorderIfNoneExplicitlySet() {
            contextRunner
                .withBean(SqsAsyncClient.class, () -> mock(SqsAsyncClient.class))
                .run(context -> {
                    when(mockGlobalRecorder.beginSegment(anyString())).thenReturn(mock(Segment.class));
                    assertThat(context).hasSingleBean(AWSXRayRecorder.class);
                    assertThat(context.getBean("sqsXrayRecorder")).isSameAs(mockGlobalRecorder);

                    final SqsAsyncClient sqsAsyncClient = context
                        .getBean(SqsAsyncClientProvider.class)
                        .getDefaultClient()
                        .orElseThrow(() -> new RuntimeException(""));
                    sqsAsyncClient.sendMessage(SendMessageRequest.builder().build());
                    verify(mockGlobalRecorder).beginSegment(anyString());
                });
        }
    }
}
