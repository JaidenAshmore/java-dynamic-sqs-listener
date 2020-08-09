package com.jashmore.sqs.extensions.xray.client;

import static java.util.stream.Collectors.toList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Segment;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Slf4j
@ExtendWith(MockitoExtension.class)
class XrayWrappedSqsAsyncClientTest {
    @Mock
    SqsAsyncClient delegate;

    @Mock
    AWSXRayRecorder recorder;

    ClientSegmentNamingStrategy segmentNamingStrategy = new StaticClientSegmentNamingStrategy("static-name");

    @Nested
    class SegmentWrapping {

        @Test
        void noSegmentNamingStrategyWillUseDefault() {
            // arrange
            when(recorder.getCurrentSegmentOptional()).thenReturn(Optional.empty());
            final SqsAsyncClient client = new XrayWrappedSqsAsyncClient(
                XrayWrappedSqsAsyncClient.Options.builder().delegate(delegate).recorder(recorder).build()
            );

            // act
            client.sendMessage(SendMessageRequest.builder().messageBody("body").build());

            // assert
            verify(recorder).beginSegment("message-listener");
            verify(delegate).sendMessage(any(SendMessageRequest.class));
            verify(recorder).endSegment();
        }

        @Test
        void clientCallWillCreateSegmentIfNotAlreadyPresent() {
            // arrange
            when(recorder.getCurrentSegmentOptional()).thenReturn(Optional.empty());
            final SqsAsyncClient client = new XrayWrappedSqsAsyncClient(
                XrayWrappedSqsAsyncClient
                    .Options.builder()
                    .delegate(delegate)
                    .recorder(recorder)
                    .segmentNamingStrategy(segmentNamingStrategy)
                    .build()
            );

            // act
            client.sendMessage(SendMessageRequest.builder().messageBody("body").build());

            // assert
            verify(recorder).beginSegment("static-name");
            verify(delegate).sendMessage(any(SendMessageRequest.class));
            verify(recorder).endSegment();
        }

        @Test
        void clientCallWillNotCreateSegmentIfAlreadyPresent() {
            // arrange
            when(recorder.getCurrentSegmentOptional()).thenReturn(Optional.of(mock(Segment.class)));
            final SqsAsyncClient client = new XrayWrappedSqsAsyncClient(
                XrayWrappedSqsAsyncClient
                    .Options.builder()
                    .delegate(delegate)
                    .recorder(recorder)
                    .segmentNamingStrategy(segmentNamingStrategy)
                    .build()
            );

            // act
            client.sendMessage(SendMessageRequest.builder().messageBody("body").build());

            // assert
            verify(recorder, never()).beginSegment("static-name");
            verify(delegate).sendMessage(any(SendMessageRequest.class));
            verify(recorder, never()).endSegment();
        }

        @Test
        void whenNoRecorderSetGlobalIsUsed() {
            // arrange
            AWSXRay.setGlobalRecorder(recorder);
            when(recorder.getCurrentSegmentOptional()).thenReturn(Optional.of(mock(Segment.class)));
            final SqsAsyncClient client = new XrayWrappedSqsAsyncClient(
                XrayWrappedSqsAsyncClient.Options.builder().delegate(delegate).segmentNamingStrategy(segmentNamingStrategy).build()
            );

            // act
            client.sendMessage(SendMessageRequest.builder().messageBody("body").build());

            // assert
            verify(recorder, never()).beginSegment("static-name");
            verify(delegate).sendMessage(any(SendMessageRequest.class));
            verify(recorder, never()).endSegment();
        }

        @Test
        void allowsForMutatingOfSegmentCreated() {
            // arrange
            when(recorder.getCurrentSegmentOptional()).thenReturn(Optional.empty());
            final Segment mockSegment = mock(Segment.class);
            when(recorder.beginSegment(anyString())).thenReturn(mockSegment);
            final ClientSegmentMutator clientSegmentMutator = mock(ClientSegmentMutator.class);
            final SqsAsyncClient client = new XrayWrappedSqsAsyncClient(
                XrayWrappedSqsAsyncClient
                    .Options.builder()
                    .delegate(delegate)
                    .recorder(recorder)
                    .segmentMutator(clientSegmentMutator)
                    .build()
            );

            // act
            client.sendMessage(SendMessageRequest.builder().messageBody("body").build());

            // assert
            verify(clientSegmentMutator).mutateSegment(mockSegment);
        }

        @Test
        void unsampledClientSegmentMutatorWillMarkSegmentAsNotSampled() {
            // arrange
            when(recorder.getCurrentSegmentOptional()).thenReturn(Optional.empty());
            final Segment mockSegment = mock(Segment.class);
            when(recorder.beginSegment(anyString())).thenReturn(mockSegment);
            final SqsAsyncClient client = new XrayWrappedSqsAsyncClient(
                XrayWrappedSqsAsyncClient
                    .Options.builder()
                    .delegate(delegate)
                    .recorder(recorder)
                    .segmentMutator(new UnsampledClientSegmentMutator())
                    .build()
            );

            // act
            client.sendMessage(SendMessageRequest.builder().messageBody("body").build());

            // assert
            verify(mockSegment).setSampled(false);
        }

        @Test
        void allMethodsAreCorrectlyWrapped() throws InvocationTargetException, IllegalAccessException {
            // arrange
            when(recorder.getCurrentSegmentOptional()).thenReturn(Optional.empty());
            final List<Method> declaredMethods = Arrays
                .stream(XrayWrappedSqsAsyncClient.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !method.getName().equals("serviceName") && !method.getName().equals("close"))
                .collect(toList());
            log.info("Methods: {}", declaredMethods.stream().map(Method::getName).collect(toList()));
            final SqsAsyncClient client = new XrayWrappedSqsAsyncClient(
                XrayWrappedSqsAsyncClient.Options.builder().delegate(delegate).recorder(recorder).build()
            );

            // act
            for (final Method method : declaredMethods) {
                if (method.getParameterCount() == 1) {
                    method.invoke(client, (Object) null);
                } else {
                    method.invoke(client);
                }
            }

            // assert
            verify(recorder, times(declaredMethods.size())).beginSegment(anyString());
            verify(recorder, times(declaredMethods.size())).endSegment();
        }
    }

    @Nested
    class ServiceName {

        @Test
        void willCallDelegateServiceName() {
            // arrange
            final SqsAsyncClient client = new XrayWrappedSqsAsyncClient(
                XrayWrappedSqsAsyncClient.Options.builder().delegate(delegate).recorder(recorder).build()
            );

            // act
            client.serviceName();

            // arrange
            verify(delegate).serviceName();
        }
    }

    @Nested
    class Close {

        @Test
        void willCallDelegateClose() {
            // arrange
            final SqsAsyncClient client = new XrayWrappedSqsAsyncClient(
                XrayWrappedSqsAsyncClient.Options.builder().delegate(delegate).recorder(recorder).build()
            );

            // act
            client.close();

            // arrange
            verify(delegate).close();
        }
    }
}
