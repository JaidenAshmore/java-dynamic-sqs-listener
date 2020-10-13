package com.jashmore.sqs.retriever.prefetch;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import lombok.Builder;
import lombok.Value;
import software.amazon.awssdk.services.sqs.model.Message;

@Value
@Builder
public class QueueDrain {

    Queue<CompletableFuture<Message>> futuresWaitingForMessages;
    Queue<Message> messagesAvailableForProcessing;
}
