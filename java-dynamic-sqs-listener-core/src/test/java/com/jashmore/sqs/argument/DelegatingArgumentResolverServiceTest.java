package com.jashmore.sqs.argument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;

import com.jashmore.sqs.QueueProperties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.model.Message;

import java.lang.reflect.Parameter;
import java.util.Set;

public class DelegatingArgumentResolverServiceTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void whenNoResolversCanMatchParameterExceptionIsThrown() {
        // arrange
        final ArgumentResolver resolver = mock(ArgumentResolver.class);
        when(resolver.canResolveParameter(any(Parameter.class))).thenReturn(false);
        final Set<ArgumentResolver> resolvers = ImmutableSet.of(resolver);
        expectedException.expect(ArgumentResolutionException.class);
        expectedException.expectMessage("No ArgumentResolver found that can process this parameter");

        // act
        new DelegatingArgumentResolverService(resolvers).resolveArgument(null, null, null);
    }

    @Test
    public void whenResolvingArgumentThrowsExceptionArgumentResolutionExceptionWrapsIt() {
        // arrange
        final ArgumentResolver resolver = mock(ArgumentResolver.class);
        when(resolver.canResolveParameter(isNull())).thenReturn(true);
        when(resolver.resolveArgumentForParameter(isNull(), isNull(), isNull()))
                .thenThrow(new RuntimeException("error"));
        final Set<ArgumentResolver> resolvers = ImmutableSet.of(resolver);
        expectedException.expect(ArgumentResolutionException.class);
        expectedException.expectCause(isA(RuntimeException.class));

        // act
        new DelegatingArgumentResolverService(resolvers).resolveArgument(null, null, null);
    }

    @Test
    public void whenResolvingArgumentThrowsArgumentResolutionExceptionItIsBubbled() {
        // arrange
        final ArgumentResolver resolver = mock(ArgumentResolver.class);
        when(resolver.canResolveParameter(isNull())).thenReturn(true);
        final ArgumentResolutionException exception = new ArgumentResolutionException("error");
        when(resolver.resolveArgumentForParameter(isNull(), isNull(), isNull()))
                .thenThrow(exception);
        final Set<ArgumentResolver> resolvers = ImmutableSet.of(resolver);
        expectedException.expect(ArgumentResolutionException.class);
        expectedException.expect(is(exception));

        // act
        new DelegatingArgumentResolverService(resolvers).resolveArgument(null, null, null);
    }

    @Test
    public void whenArgumentIsSuccessfullyResolvedTheValueIsReturned() {
        // arrange
        final ArgumentResolver resolver = mock(ArgumentResolver.class);
        when(resolver.canResolveParameter(isNull())).thenReturn(true);
        final Object argument = new Object();
        when(resolver.resolveArgumentForParameter(isNull(), isNull(), isNull()))
                .thenReturn(argument);
        final Set<ArgumentResolver> resolvers = ImmutableSet.of(resolver);

        // act
        final Object actualArgument = new DelegatingArgumentResolverService(resolvers).resolveArgument(null, null, null);

        // assert
        assertThat(actualArgument).isEqualTo(argument);
    }
}
