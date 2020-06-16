package com.jashmore.sqs.spring.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class DefaultSqsAsyncClientProviderTest {
    @Mock
    private SqsAsyncClient client;

    @Nested
    @DisplayName("constructor")
    class Constructor {
        @Test
        void nullClientMapThrowsException() {
            Assertions.assertThrows(NullPointerException.class, () -> new DefaultSqsAsyncClientProvider(null, null));
        }
    }

    @Nested
    @DisplayName("getDefaultClient")
    class GetDefaultClient {
        @Test
        void whenNoDefaultSqsAsyncClientProvidedGettingDefaultReturnsEmptyOptional() {
            // arrange
            final SqsAsyncClientProvider sqsAsyncClientProvider = new DefaultSqsAsyncClientProvider((SqsAsyncClient) null);

            // act
            final Optional<SqsAsyncClient> optionalDefaultClient = sqsAsyncClientProvider.getDefaultClient();

            // assert
            assertThat(optionalDefaultClient).isEmpty();
        }

        @Test
        void whenDefaultSqsAsyncClientProvidedGettingDefaultReturnThatClient() {
            // arrange
            final SqsAsyncClientProvider sqsAsyncClientProvider = new DefaultSqsAsyncClientProvider(client);

            // act
            final Optional<SqsAsyncClient> optionalDefaultClient = sqsAsyncClientProvider.getDefaultClient();

            // assert
            assertThat(optionalDefaultClient).contains(client);
        }

        @Test
        void whenNoDefaultClientProvidedWithClientMapDefaultClientWillBeEmptyOptional() {
            // arrange
            final SqsAsyncClientProvider sqsAsyncClientProvider = new DefaultSqsAsyncClientProvider(ImmutableMap.of("id", client));

            // act
            final Optional<SqsAsyncClient> optionalDefaultClient = sqsAsyncClientProvider.getDefaultClient();

            // assert
            assertThat(optionalDefaultClient).isEmpty();
        }
    }

    @Nested
    @DisplayName("getClient")
    class GetClient {
        @Test
        void whenClientMapProvidedOneCanBeObtainedViaTheIdentifier() {
            // arrange
            final SqsAsyncClientProvider sqsAsyncClientProvider = new DefaultSqsAsyncClientProvider(null, ImmutableMap.of("id", client));

            // act
            final Optional<SqsAsyncClient> optionalDefaultClient = sqsAsyncClientProvider.getClient("id");

            // assert
            assertThat(optionalDefaultClient).contains(client);
        }

        @Test
        void whenClientMapProvidedUsingAnIdentifierThatDoesNotExistReturnsEmptyOptional() {
            // arrange
            final SqsAsyncClientProvider sqsAsyncClientProvider = new DefaultSqsAsyncClientProvider(null, ImmutableMap.of("id", client));

            // act
            final Optional<SqsAsyncClient> optionalDefaultClient = sqsAsyncClientProvider.getClient("unknownid");

            // assert
            assertThat(optionalDefaultClient).isEmpty();
        }

        @Test
        void clientMapCannotBeUpdatedAfterConstruction() {
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
}