package com.jashmore.sqs.argument.payload;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.DefaultMethodParameter;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.util.ProxyMethodInterceptor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.lang.reflect.Method;

public class PayloadArgumentResolver_ProxyClassTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private PayloadMapper payloadMapper;

    @Mock
    private QueueProperties queueProperties;

    private PayloadArgumentResolver payloadArgumentResolver;

    @Before
    public void setUp() {
        payloadArgumentResolver = new PayloadArgumentResolver(payloadMapper);
    }

    @Test
    public void proxiedClassesCanStillHaveTheirMethodParametersProcessed() throws Exception {
        // arrange
        final Foo foo = new Foo();
        final Foo proxyFoo = ProxyMethodInterceptor.wrapObject(foo, Foo.class);
        final Method method = proxyFoo.getClass().getMethod("processMessage", String.class, String.class);

        // act
        final boolean canResolveParameterForProxiedMethod = payloadArgumentResolver.canResolveParameter(DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[0])
                .parameterIndex(0)
                .build());

        // assert
        assertThat(canResolveParameterForProxiedMethod).isTrue();
    }

    @Test
    public void proxiedClassesWillNotResolveArgumentsIfNoAnnotationForParameterSupplied() throws Exception {
        // arrange
        final Foo foo = new Foo();
        final Foo proxyFoo = ProxyMethodInterceptor.wrapObject(foo, Foo.class);
        final Method method = proxyFoo.getClass().getMethod("processMessage", String.class, String.class);

        // act
        final boolean canResolveParameterForProxiedMethod = payloadArgumentResolver.canResolveParameter(DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[1])
                .parameterIndex(1)
                .build());

        // assert
        assertThat(canResolveParameterForProxiedMethod).isFalse();
    }

    public static class Foo {
        public void processMessage(@Payload final String payload, final String otherArgument) {

        }
    }
}
