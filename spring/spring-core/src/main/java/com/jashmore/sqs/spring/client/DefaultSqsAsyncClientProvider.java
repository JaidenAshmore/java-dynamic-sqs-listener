package com.jashmore.sqs.spring.client;

import com.jashmore.sqs.util.Preconditions;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.auth.credentials;

/**
 * Default implementation that stores the default {@link SqsAsyncClient}, if it exists, and the map of available clients.
 */
public class DefaultSqsAsyncClientProvider implements SqsAsyncClientProvider {

    private final SqsAsyncClient defaultClient;
    private final Map<String, SqsAsyncClient> clientMap;

    public DefaultSqsAsyncClientProvider(final SqsAsyncClient defaultClient) {
        this.defaultClient = defaultClient;
        this.clientMap = Collections.emptyMap();
    }

    public DefaultSqsAsyncClientProvider(final Map<String, SqsAsyncClient> clientMap) {
        this(null, clientMap);
    }

    public DefaultSqsAsyncClientProvider(final SqsAsyncClient defaultClient, final Map<String, SqsAsyncClient> clientMap) {
        Preconditions.checkNotNull(clientMap, "clientMap should not be null");

        this.defaultClient = defaultClient;
        this.clientMap = Collections.unmodifiableMap(new HashMap<>(clientMap));
    }

    @Override
    public Optional<SqsAsyncClient> getDefaultClient() {
        return Optional.ofNullable(defaultClient);
    }

    @Override
    public Optional<SqsAsyncClient> getClient(final String clientIdentifier) {
        return Optional.ofNullable(clientMap.get(clientIdentifier));
    }
}
