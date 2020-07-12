package com.jashmore.sqs.extensions.xray.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Segment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class XrayWrappedSqsAsyncClientTest {
    @Mock
    SqsAsyncClient delegate;

    @Mock
    AWSXRayRecorder recorder;

    ClientSegmentNamingStrategy segmentNamingStrategy = new StaticClientSegmentNamingStrategy("static-name");

    SqsAsyncClient client;

    @BeforeEach
    void setUp() {
        client = new XrayWrappedSqsAsyncClient(delegate, recorder, segmentNamingStrategy);
    }

    @Nested
    class SegmentWrapping {
        @Test
        void clientCallWillCreateSegmentIfNotAlreadyPresent() {
            // arrange
            when(recorder.getCurrentSegmentOptional()).thenReturn(Optional.empty());

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

            // act
            client.sendMessage(SendMessageRequest.builder().messageBody("body").build());

            // assert
            verify(recorder, never()).beginSegment("static-name");
            verify(delegate).sendMessage(any(SendMessageRequest.class));
            verify(recorder, never()).endSegment();
        }
    }

    @Nested
    class ServiceName {
        @Test
        void willCallDelegateServiceName() {
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
            // act
            client.close();

            // arrange
            verify(delegate).close();
        }
    }
}