package com.jashmore.sqs.argument.visibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.QueueProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class VisibilityExtenderArgumentResolverTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private QueueProperties queueProperties;

    private final Message message = Message.builder().build();

    private VisibilityExtenderArgumentResolver visibilityExtenderArgumentResolver;

    @Before
    public void setUp() {
        visibilityExtenderArgumentResolver = new VisibilityExtenderArgumentResolver(sqsAsyncClient);
    }

    @Test
    public void canResolveParametersWithVisibilityExtenderType() {
        // arrange
        final Parameter parameter = getParameter(0);

        // act
        final boolean canResolveParameter = visibilityExtenderArgumentResolver.canResolveParameter(parameter);

        // assert
        assertThat(canResolveParameter).isTrue();
    }

    @Test
    public void canNotResolveParametersThatIsNotAVisibilityExtenderType() {
        // arrange
        final Parameter parameter = getParameter(1);

        // act
        final boolean canResolveParameter = visibilityExtenderArgumentResolver.canResolveParameter(parameter);

        // assert
        assertThat(canResolveParameter).isFalse();
    }

    @Test
    public void resolvingParameterReturnsVisibilityExtenderObject() {
        // arrange
        final Parameter parameter = getParameter(0);

        // act
        final Object resolvedArgument = visibilityExtenderArgumentResolver.resolveArgumentForParameter(queueProperties, parameter, message);

        // assert
        assertThat(resolvedArgument).isInstanceOf(VisibilityExtender.class);
    }

    @SuppressWarnings( {"WeakerAccess", "unused"})
    public void method(final VisibilityExtender visibilityExtender, final String string) {

    }

    private Parameter getParameter(final int index) {
        final Method method;
        try {
            method = VisibilityExtenderArgumentResolverTest.class.getMethod("method", VisibilityExtender.class, String.class);
        } catch (final NoSuchMethodException exception) {
            throw new RuntimeException("Unable to find method for testing against", exception);
        }
        return method.getParameters()[index];
    }
}
