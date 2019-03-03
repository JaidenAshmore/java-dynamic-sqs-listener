package com.jashmore.sqs.argument.visibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.DefaultMethodParameter;
import com.jashmore.sqs.argument.MethodParameter;
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
        final MethodParameter parameter = getParameter(0);

        // act
        final boolean canResolveParameter = visibilityExtenderArgumentResolver.canResolveParameter(parameter);

        // assert
        assertThat(canResolveParameter).isTrue();
    }

    @Test
    public void canNotResolveParametersThatIsNotAVisibilityExtenderType() {
        // arrange
        final MethodParameter parameter = getParameter(1);

        // act
        final boolean canResolveParameter = visibilityExtenderArgumentResolver.canResolveParameter(parameter);

        // assert
        assertThat(canResolveParameter).isFalse();
    }

    @Test
    public void resolvingParameterReturnsVisibilityExtenderObject() {
        // arrange
        final MethodParameter parameter = getParameter(0);

        // act
        final Object resolvedArgument = visibilityExtenderArgumentResolver.resolveArgumentForParameter(queueProperties, parameter, message);

        // assert
        assertThat(resolvedArgument).isInstanceOf(VisibilityExtender.class);
    }

    @SuppressWarnings( {"WeakerAccess", "unused"})
    public void method(final VisibilityExtender visibilityExtender, final String string) {

    }

    private MethodParameter getParameter(final int index) {
        try {
            final Method method = VisibilityExtenderArgumentResolverTest.class.getMethod("method", VisibilityExtender.class, String.class);
            return DefaultMethodParameter.builder()
                    .method(method)
                    .parameter(method.getParameters()[index])
                    .parameterIndex(index)
                    .build();
        } catch (final NoSuchMethodException exception) {
            throw new RuntimeException("Unable to find method for testing against", exception);
        }
    }
}
