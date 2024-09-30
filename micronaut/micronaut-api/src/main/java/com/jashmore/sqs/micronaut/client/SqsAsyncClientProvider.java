package com.jashmore.sqs.micronaut.client;

import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.Optional;

/**
 * Used to provide a specific {@link SqsAsyncClient} that can be used to communicate with the SQS Queue.
 *
 * <p>As the framework allows for multiple {@link SqsAsyncClient}s to be used, due to the possibility of queues existing across multiple accounts, this provides
 * the ability to identify certain clients for usage by certain queues. For example, you may have two AWS Accounts (accountOne, accountTwo) which SQS queues in
 * each. The specific Queue Listeners will need to listen to a SQS Queue on each account and therefore a single {@link SqsAsyncClient} should be provided
 * for the entire application.
 */
public interface SqsAsyncClientProvider {
    /**
     * The default {@link SqsAsyncClient} that can be used if there is no identifier provided.
     *
     * <p>This is more useful for the case that you only have a single AWS Account and don't need to identify it.
     *
     * @return the default client if it exists
     */
    Optional<SqsAsyncClient> getDefaultClient();

    /**
     * Get the specific client that has the provided identifier.
     *
     * <p>This map is useful when you have those duplicate accounts and certain {@link SqsAsyncClient}s need to be used on certain queues.
     *
     * <p>If an identifier is provided that does not match a known {@link SqsAsyncClient}, an {@link Optional#empty()} is returned.
     *
     * @param clientIdentifier the identifier of the client to get
     * @return the client if it exists, an {@link Optional#empty()} otherwise
     */
    Optional<SqsAsyncClient> getClient(String clientIdentifier);
}
