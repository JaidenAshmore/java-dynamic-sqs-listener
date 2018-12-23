package com.jashmore.sqs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.DefaultArgumentResolverService;
import com.jashmore.sqs.argument.payload.mapper.JacksonPayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class QueueListenerConfigurationTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private final QueueListenerConfiguration queueListenerConfiguration = new QueueListenerConfiguration();

    @Test
    public void payloadMapperIsJacksonMapperByDefault() {
        // act
        final PayloadMapper payloadMapper = queueListenerConfiguration.payloadMapper();

        // assert
        assertThat(payloadMapper).isInstanceOf(JacksonPayloadMapper.class);
    }

    @Test
    public void argumentResolverServiceIsDefaultArgumentResolverServiceByDefault() {
        // act
        final ArgumentResolverService argumentResolverService = queueListenerConfiguration.argumentResolverService(mock(PayloadMapper.class), mock(AmazonSQSAsync.class));

        // assert
        assertThat(argumentResolverService).isInstanceOf(DefaultArgumentResolverService.class);
    }
}
