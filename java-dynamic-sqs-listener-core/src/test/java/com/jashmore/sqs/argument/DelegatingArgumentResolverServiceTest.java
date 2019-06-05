package com.jashmore.sqs.argument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Set;

public class DelegatingArgumentResolverServiceTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private MethodParameter methodParameter;

    @Test
    public void whenNoResolversCanMatchParameterExceptionIsThrown() throws Exception {
        // arrange
        final ArgumentResolver<?> resolver = mock(ArgumentResolver.class);
        when(resolver.canResolveParameter(any(MethodParameter.class))).thenReturn(false);
        final Set<ArgumentResolver<?>> resolvers = ImmutableSet.of(resolver);
        when(methodParameter.getMethod()).thenReturn(this.getClass().getMethod("whenNoResolversCanMatchParameterExceptionIsThrown"));
        when(methodParameter.getParameterIndex()).thenReturn(1);
        expectedException.expect(UnsupportedArgumentResolutionException.class);
        expectedException.expectMessage("No known for parameter[1] for method: com.jashmore.sqs.argument.DelegatingArgumentResolverServiceTest#whenNoResolversCanMatchParameterExceptionIsThrown");

        // act
        new DelegatingArgumentResolverService(resolvers).getArgumentResolver(methodParameter);
    }

    @Test
    public void whenResolveCanMatchParameterThatIsReturned() {
        // arrange
        final ArgumentResolver<?> resolver = mock(ArgumentResolver.class);
        when(resolver.canResolveParameter(isNull())).thenReturn(true);
        final Set<ArgumentResolver<?>> resolvers = ImmutableSet.of(resolver);

        // act
        final ArgumentResolver<?> matchedResolver = new DelegatingArgumentResolverService(resolvers).getArgumentResolver(null);

        // assert
        assertThat(matchedResolver).isSameAs(matchedResolver);
    }

    @Test
    public void whenMultipleResolversMatchParameterTheFirstIsReturned() {
        // arrange
        final ArgumentResolver<?> firstResolver = mock(ArgumentResolver.class);
        when(firstResolver.canResolveParameter(isNull())).thenReturn(true);
        final ArgumentResolver<?> secondResolver = mock(ArgumentResolver.class);
        when(secondResolver.canResolveParameter(isNull())).thenReturn(true);
        final Set<ArgumentResolver<?>> resolvers = ImmutableSet.of(firstResolver, secondResolver);

        // act
        final ArgumentResolver<?> matchedResolver = new DelegatingArgumentResolverService(resolvers).getArgumentResolver(null);

        // assert
        assertThat(matchedResolver).isSameAs(firstResolver);
    }
}
