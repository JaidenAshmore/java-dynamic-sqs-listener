package com.jashmore.sqs.argument.heartbeat;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.jashmore.sqs.QueueProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class HeartbeatArgumentResolverTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AmazonSQSAsync amazonSqsAsync;

    @Mock
    private QueueProperties queueProperties;

    @Mock
    private Message message;

    private HeartbeatArgumentResolver heartbeatArgumentResolver;

    @Before
    public void setUp() {
        heartbeatArgumentResolver = new HeartbeatArgumentResolver(amazonSqsAsync);
    }

    @Test
    public void canResolveParametersWithHeartbeatType() {
        // arrange
        final Parameter parameter = getParameter(0);

        // act
        final boolean canResolveParameter = heartbeatArgumentResolver.canResolveParameter(parameter);

        // assert
        assertThat(canResolveParameter).isTrue();
    }

    @Test
    public void canNotResolveParametersThatIsNotAHeartbeatType() {
        // arrange
        final Parameter parameter = getParameter(1);

        // act
        final boolean canResolveParameter = heartbeatArgumentResolver.canResolveParameter(parameter);

        // assert
        assertThat(canResolveParameter).isFalse();
    }

    @Test
    public void resolvingParameterReturnsHeartbeatObject() {
        // arrange
        final Parameter parameter = getParameter(0);

        // act
        final Object resolvedArgument = heartbeatArgumentResolver.resolveArgumentForParameter(queueProperties, parameter, message);

        // assert
        assertThat(resolvedArgument).isInstanceOf(Heartbeat.class);
    }

    @SuppressWarnings( {"WeakerAccess", "unused"})
    public void method(final Heartbeat heartbeat, final String string) {

    }

    private Parameter getParameter(final int index) {
        final Method method;
        try {
            method = HeartbeatArgumentResolverTest.class.getMethod("method", Heartbeat.class, String.class);
        } catch (final NoSuchMethodException exception) {
            throw new RuntimeException("Unable to find method for testing against", exception);
        }
        return method.getParameters()[index];
    }
}
