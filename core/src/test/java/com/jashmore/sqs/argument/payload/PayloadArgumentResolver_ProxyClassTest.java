package com.jashmore.sqs.argument.payload;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.argument.DefaultMethodParameter;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.util.ProxyMethodInterceptor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("checkstyle:TypeName")
@ExtendWith(MockitoExtension.class)
class PayloadArgumentResolver_ProxyClassTest {
    @Mock
    private PayloadMapper payloadMapper;

    private PayloadArgumentResolver payloadArgumentResolver;

    @BeforeEach
    void setUp() {
        payloadArgumentResolver = new PayloadArgumentResolver(payloadMapper);
    }

    @Test
    void proxiedClassesCanStillHaveTheirMethodParametersProcessed() throws Exception {
        // arrange
        final Foo foo = new Foo();
        final Foo proxyFoo = ProxyMethodInterceptor.wrapObject(foo, Foo.class);
        final Method method = proxyFoo.getClass().getMethod("processMessage", String.class, String.class);

        // act
        final boolean canResolveParameterForProxiedMethod = payloadArgumentResolver.canResolveParameter(
            DefaultMethodParameter.builder().method(method).parameter(method.getParameters()[0]).parameterIndex(0).build()
        );

        // assert
        assertThat(canResolveParameterForProxiedMethod).isTrue();
    }

    @Test
    void proxiedClassesWillNotResolveArgumentsIfNoAnnotationForParameterSupplied() throws Exception {
        // arrange
        final Foo foo = new Foo();
        final Foo proxyFoo = ProxyMethodInterceptor.wrapObject(foo, Foo.class);
        final Method method = proxyFoo.getClass().getMethod("processMessage", String.class, String.class);

        // act
        final boolean canResolveParameterForProxiedMethod = payloadArgumentResolver.canResolveParameter(
            DefaultMethodParameter.builder().method(method).parameter(method.getParameters()[1]).parameterIndex(1).build()
        );

        // assert
        assertThat(canResolveParameterForProxiedMethod).isFalse();
    }

    @SuppressWarnings({ "unused", "WeakerAccess" })
    public static class Foo {

        public void processMessage(@Payload final String payload, final String otherArgument) {}
    }
}
