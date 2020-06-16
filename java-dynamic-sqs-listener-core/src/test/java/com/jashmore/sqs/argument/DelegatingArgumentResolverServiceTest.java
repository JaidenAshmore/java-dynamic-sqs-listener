package com.jashmore.sqs.argument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

@ExtendWith(MockitoExtension.class)
class DelegatingArgumentResolverServiceTest {

    @Mock
    private MethodParameter methodParameter;

    @Test
    void whenNoResolversCanMatchParameterExceptionIsThrown() throws Exception {
        // arrange
        final ArgumentResolver<?> resolver = mock(ArgumentResolver.class);
        when(resolver.canResolveParameter(any(MethodParameter.class))).thenReturn(false);
        final Set<ArgumentResolver<?>> resolvers = ImmutableSet.of(resolver);
        when(methodParameter.getMethod()).thenReturn(this.getClass().getMethod("someMethod"));
        when(methodParameter.getParameterIndex()).thenReturn(1);

        // act
        final UnsupportedArgumentResolutionException exception = assertThrows(UnsupportedArgumentResolutionException.class,
                () -> new DelegatingArgumentResolverService(resolvers).getArgumentResolver(methodParameter));

        // assert
        assertThat(exception).hasMessage("No eligible ArgumentResolver for parameter[1] for method: com.jashmore.sqs.argument.DelegatingArgumentResolverServiceTest#someMethod");
    }

    @Test
    void whenResolveCanMatchParameterThatIsReturned() {
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
    void whenMultipleResolversMatchParameterTheFirstIsReturned() {
        // arrange
        final ArgumentResolver<?> firstResolver = mock(ArgumentResolver.class);
        when(firstResolver.canResolveParameter(isNull())).thenReturn(true);
        final ArgumentResolver<?> secondResolver = mock(ArgumentResolver.class);
        final Set<ArgumentResolver<?>> resolvers = ImmutableSet.of(firstResolver, secondResolver);

        // act
        final ArgumentResolver<?> matchedResolver = new DelegatingArgumentResolverService(resolvers).getArgumentResolver(null);

        // assert
        assertThat(matchedResolver).isSameAs(firstResolver);
        verify(secondResolver, never()).canResolveParameter(any());
    }

    @SuppressWarnings("WeakerAccess")
    public void someMethod() {

    }
}
