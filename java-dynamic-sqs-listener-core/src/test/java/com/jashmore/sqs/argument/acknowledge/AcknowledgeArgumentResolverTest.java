package com.jashmore.sqs.argument.acknowledge;

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

public class AcknowledgeArgumentResolverTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AmazonSQSAsync amazonSqsAsync;

    @Mock
    private QueueProperties queueProperties;

    private AcknowledgeArgumentResolver acknowledgeArgumentResolver;

    @Before
    public void setUp() {
        acknowledgeArgumentResolver = new AcknowledgeArgumentResolver(amazonSqsAsync);
    }

    @Test
    public void shouldAcceptClassesOfTypeAcknowledge() {
        // arrange
        final Parameter parameter = getParameter(0);

        // act
        final boolean canResolveParameter = acknowledgeArgumentResolver.canResolveParameter(parameter);

        // assert
        assertThat(canResolveParameter).isTrue();
    }

    @Test
    public void shouldNotAcceptClassesNotOfTypeAcknowledge() {
        // arrange
        final Parameter parameter = getParameter(1);

        // act
        final boolean canResolveParameter = acknowledgeArgumentResolver.canResolveParameter(parameter);

        // assert
        assertThat(canResolveParameter).isFalse();
    }

    @Test
    public void shouldResolveParameterWithAnAcknowledgeClass() {
        // arrange
        final Parameter parameter = getParameter(0);
        final Message message = new Message();

        // act
        final Object resolvedArgument = acknowledgeArgumentResolver.resolveArgumentForParameter(queueProperties, parameter, message);

        // assert
        assertThat(resolvedArgument).isInstanceOf(Acknowledge.class);
    }

    @SuppressWarnings( {"WeakerAccess", "unused"})
    public void method(final Acknowledge acknowledge, final String string) {

    }

    private Parameter getParameter(final int index) {
        final Method method;
        try {
            method = AcknowledgeArgumentResolverTest.class.getMethod("method", Acknowledge.class, String.class);
        } catch (final NoSuchMethodException exception) {
            throw new RuntimeException("Unable to find method for testing against", exception);
        }
        return method.getParameters()[index];
    }
}
