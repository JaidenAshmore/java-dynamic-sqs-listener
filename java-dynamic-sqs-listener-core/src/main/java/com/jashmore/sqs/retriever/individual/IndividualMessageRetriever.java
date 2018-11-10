package com.jashmore.sqs.retriever.individual;

import static com.jashmore.sqs.aws.AwsConstants.MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS;
import static java.lang.Math.toIntExact;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.amazonaws.annotation.ThreadSafe;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.retriever.MessageRetriever;
import com.jashmore.sqs.util.Preconditions;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Message retriever that will obtain a new message from the server as requested and will not batch any messages for future use.
 *
 * <p>This can be useful for when processing of messages can take a long time and it is undesirable to batch them as that could result in the batched messages
 * visual timeout expiring.
 */
@Slf4j
@ThreadSafe
@AllArgsConstructor
public class IndividualMessageRetriever implements MessageRetriever {
    private final AmazonSQSAsync amazonSqsAsync;
    private final QueueProperties queueProperties;
    private final IndividualMessageRetrieverProperties properties;

    @Override
    public Optional<Message> retrieveMessageNow() throws InterruptedException {
        return retrieveMessage(0, MILLISECONDS);
    }

    @Override
    public Message retrieveMessage() throws InterruptedException {
        Optional<Message> optionalMessage = Optional.empty();
        while (!optionalMessage.isPresent()) {
            optionalMessage = retrieveMessage(MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS, SECONDS);
        }

        return optionalMessage.get();
    }

    /**
     * As the {@link ReceiveMessageRequest#waitTimeSeconds} is to seconds the timeout for this method will be to the closest second and therefore milliseconds
     * will be ignored. The timeout also doesn't take into account latency for the request so the actual wait time will not perfectly align with what is
     * requested but will be close.
     */
    @Override
    public Optional<Message> retrieveMessage(@Min(0) final long timeout, @NotNull final TimeUnit timeUnit) throws InterruptedException {
        Preconditions.checkArgument(timeout >= 0, "timeout should be greater than or equal to zero");
        Preconditions.checkArgumentNotNull(timeUnit, "timeUnit");

        final int secondsToWait = toIntExact(timeUnit.toSeconds(timeout));
        return retrieveMessage(secondsToWait);
    }

    /**
     * Wait the total provided seconds to receive a message from a queue.
     *
     * <p>As {@link ReceiveMessageRequest#waitTimeSeconds} has a maximum value multiple calls will be made be made until the total time has elapsed.
     *
     * @param totalSecondsToWait the total number of seconds to wait for the message
     * @return the message if it was successfully received or an {@link Optional#empty()} if no message was received within the period
     * @throws InterruptedException if the thread was interrupted while waiting for the message
     */
    private Optional<Message> retrieveMessage(final int totalSecondsToWait) throws InterruptedException {
        final int waitTimeInSecondsForThisRequest = Math.min(totalSecondsToWait, MAX_SQS_RECEIVE_WAIT_TIME_IN_SECONDS);

        final Future<ReceiveMessageResult> receiveMessageResultFuture = amazonSqsAsync.receiveMessageAsync(
                generateReceiveMessageRequest(waitTimeInSecondsForThisRequest)
        );

        try {
            final ReceiveMessageResult receiveMessageResult = receiveMessageResultFuture.get();
            if (!receiveMessageResult.getMessages().isEmpty()) {
                return Optional.of(receiveMessageResult.getMessages().get(0));
            }
        } catch (final ExecutionException executionException) {
            throw new RuntimeException("Exception retrieving message", executionException.getCause());
        }

        final int newSecondsToWait = totalSecondsToWait - waitTimeInSecondsForThisRequest;
        if (newSecondsToWait <= 0) {
            return Optional.empty();
        }
        return retrieveMessage(newSecondsToWait);
    }

    private ReceiveMessageRequest generateReceiveMessageRequest(final int waitTimeInSeconds) {
        return new ReceiveMessageRequest(queueProperties.getQueueUrl())
                .withMaxNumberOfMessages(1)
                .withWaitTimeSeconds(waitTimeInSeconds)
                .withVisibilityTimeout(properties.getVisibilityTimeoutForMessagesInSeconds());
    }
}
