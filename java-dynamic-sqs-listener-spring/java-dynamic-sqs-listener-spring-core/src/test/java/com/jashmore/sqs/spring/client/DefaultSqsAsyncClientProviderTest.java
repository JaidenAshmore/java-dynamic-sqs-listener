package com.jashmore.sqs.spring.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.Map;
import java.util.Optional;

public class DefaultSqsAsyncClientProviderTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SqsAsyncClient client;

    @Test
    public void whenNoDefaultSqsAsyncClientProvidedGettingDefaultReturnsEmptyOptional() {
        // arrange
        final SqsAsyncClientProvider sqsAsyncClientProvider = new DefaultSqsAsyncClientProvider((SqsAsyncClient)null);

        // act
        final Optional<SqsAsyncClient> optionalDefaultClient = sqsAsyncClientProvider.getDefaultClient();

        // assert
        assertThat(optionalDefaultClient).isEmpty();
    }

    @Test
    public void whenDefaultSqsAsyncClientProvidedGettingDefaultReturnThatClient() {
        // arrange
        final SqsAsyncClientProvider sqsAsyncClientProvider = new DefaultSqsAsyncClientProvider(client);

        // act
        final Optional<SqsAsyncClient> optionalDefaultClient = sqsAsyncClientProvider.getDefaultClient();

        // assert
        assertThat(optionalDefaultClient).contains(client);
    }

    @Test(expected = NullPointerException.class)
    public void nullClientMapThrowsException() {
        new DefaultSqsAsyncClientProvider(null, null);
    }

    @Test
    public void whenClientMapProvidedOneCanBeObtainedViaTheIdentifier() {
        // arrange
        final SqsAsyncClientProvider sqsAsyncClientProvider = new DefaultSqsAsyncClientProvider(null, ImmutableMap.of("id", client));

        // act
        final Optional<SqsAsyncClient> optionalDefaultClient = sqsAsyncClientProvider.getClient("id");

        // assert
        assertThat(optionalDefaultClient).contains(client);
    }

    @Test
    public void whenNoDefaultClientProvidedWithClientMapDefaultClientWillBeEmptyOptional() {
        // arrange
        final SqsAsyncClientProvider sqsAsyncClientProvider = new DefaultSqsAsyncClientProvider(ImmutableMap.of("id", client));

        // act
        final Optional<SqsAsyncClient> optionalDefaultClient = sqsAsyncClientProvider.getDefaultClient();

        // assert
        assertThat(optionalDefaultClient).isEmpty();
    }

    @Test
    public void whenClientMapProvidedUsingAnIdentifierThatDoesNotExistReturnsEmptyOptional() {
        // arrange
        final SqsAsyncClientProvider sqsAsyncClientProvider = new DefaultSqsAsyncClientProvider(null, ImmutableMap.of("id", client));

        // act
        final Optional<SqsAsyncClient> optionalDefaultClient = sqsAsyncClientProvider.getClient("unknownid");

        // assert
        assertThat(optionalDefaultClient).isEmpty();
    }

    @Test
    public void clientMapCannotBeUpdatedAfterConstruction() {
        // arrange
        final Map<String, SqsAsyncClient> clientMap = Maps.newHashMap();
        final SqsAsyncClientProvider sqsAsyncClientProvider = new DefaultSqsAsyncClientProvider(null, clientMap);

        // act
        clientMap.put("id", client);

        // assert
        final Optional<SqsAsyncClient> optionalDefaultClient = sqsAsyncClientProvider.getClient("id");
        assertThat(optionalDefaultClient).isEmpty();
    }
}