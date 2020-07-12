package com.jashmore.sqs.extensions.xray.client;

import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Segment;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.AddPermissionRequest;
import software.amazon.awssdk.services.sqs.model.AddPermissionResponse;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ListDeadLetterSourceQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListDeadLetterSourceQueuesResponse;
import software.amazon.awssdk.services.sqs.model.ListQueueTagsRequest;
import software.amazon.awssdk.services.sqs.model.ListQueueTagsResponse;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.RemovePermissionRequest;
import software.amazon.awssdk.services.sqs.model.RemovePermissionResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.TagQueueRequest;
import software.amazon.awssdk.services.sqs.model.TagQueueResponse;
import software.amazon.awssdk.services.sqs.model.UntagQueueRequest;
import software.amazon.awssdk.services.sqs.model.UntagQueueResponse;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The {@link SqsAsyncClient} that can be used when the Xray Instrumentor has been integrated into the service which will allow you to use the client even
 * if it is being called when a segment has not begun.
 *
 * <p>This client is needed because when the <pre>TracingInterceptor</pre> from the Xray Instrumentor is automatically injected, whenever a call to a client
 * is made without a segment being begun an exception will be thrown. This is because the interceptor will try and create a subsegment which is not possible
 * if the overall segment hasn't been started. The way that this works with Http Requests is that each request will go through a filter that will start a
 * segment for you. However, as the {@link SqsAsyncClient} is being used on separate threads orchestrated by this SQS Library, no segment will have
 * automatically been set up for you. As the library does not and should not know about XRay, it would be easier to wrap all calls to the client with some
 * safety code instead of littering this segment code in the core library.
 *
 * <p>This {@link SqsAsyncClient}, on every call, will check if there is a segment already started and if there hasn't been it will start one with the
 * name defined by the {@link ClientSegmentNamingStrategy} provided to this.
 */
public class XrayWrappedSqsAsyncClient implements SqsAsyncClient {
    private final SqsAsyncClient delegate;
    private final AWSXRayRecorder recorder;
    private final ClientSegmentNamingStrategy segmentNamingStrategy;

    /**
     * Constructor.
     *
     * @param delegate              the underlying client to use
     * @param recorder              the recorder used to start and stop segments
     * @param segmentNamingStrategy the strategy for how to name the segment that is conditionally created when running a method
     */
    public XrayWrappedSqsAsyncClient(final SqsAsyncClient delegate,
                                     final AWSXRayRecorder recorder,
                                     final ClientSegmentNamingStrategy segmentNamingStrategy) {
        this.delegate = delegate;
        this.recorder = recorder;
        this.segmentNamingStrategy = segmentNamingStrategy;
    }

    @Override
    public String serviceName() {
        return delegate.serviceName();
    }

    @Override
    public CompletableFuture<AddPermissionResponse> addPermission(final AddPermissionRequest addPermissionRequest) {
        return wrapWithXray(() -> delegate.addPermission(addPermissionRequest));
    }

    @Override
    public CompletableFuture<AddPermissionResponse> addPermission(final Consumer<AddPermissionRequest.Builder> addPermissionRequest) {
        return wrapWithXray(() -> delegate.addPermission(addPermissionRequest));
    }

    @Override
    public CompletableFuture<ChangeMessageVisibilityResponse> changeMessageVisibility(final ChangeMessageVisibilityRequest changeMessageVisibilityRequest) {
        return wrapWithXray(() -> delegate.changeMessageVisibility(changeMessageVisibilityRequest));
    }

    @Override
    public CompletableFuture<ChangeMessageVisibilityResponse> changeMessageVisibility(
            final Consumer<ChangeMessageVisibilityRequest.Builder> changeMessageVisibilityRequest) {
        return wrapWithXray(() -> delegate.changeMessageVisibility(changeMessageVisibilityRequest));
    }

    @Override
    public CompletableFuture<ChangeMessageVisibilityBatchResponse> changeMessageVisibilityBatch(
            final ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest) {
        return wrapWithXray(() -> delegate.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest));
    }

    @Override
    public CompletableFuture<ChangeMessageVisibilityBatchResponse> changeMessageVisibilityBatch(
            final Consumer<ChangeMessageVisibilityBatchRequest.Builder> changeMessageVisibilityBatchRequest) {
        return wrapWithXray(() -> delegate.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest));
    }

    @Override
    public CompletableFuture<CreateQueueResponse> createQueue(final CreateQueueRequest createQueueRequest) {
        return wrapWithXray(() -> delegate.createQueue(createQueueRequest));
    }

    @Override
    public CompletableFuture<CreateQueueResponse> createQueue(final Consumer<CreateQueueRequest.Builder> createQueueRequest) {
        return wrapWithXray(() -> delegate.createQueue(createQueueRequest));
    }

    @Override
    public CompletableFuture<DeleteMessageResponse> deleteMessage(final DeleteMessageRequest deleteMessageRequest) {
        return wrapWithXray(() -> delegate.deleteMessage(deleteMessageRequest));
    }

    @Override
    public CompletableFuture<DeleteMessageResponse> deleteMessage(final Consumer<DeleteMessageRequest.Builder> deleteMessageRequest) {
        return wrapWithXray(() -> delegate.deleteMessage(deleteMessageRequest));
    }

    @Override
    public CompletableFuture<DeleteMessageBatchResponse> deleteMessageBatch(final DeleteMessageBatchRequest deleteMessageBatchRequest) {
        return wrapWithXray(() -> delegate.deleteMessageBatch(deleteMessageBatchRequest));
    }

    @Override
    public CompletableFuture<DeleteMessageBatchResponse> deleteMessageBatch(final Consumer<DeleteMessageBatchRequest.Builder> deleteMessageBatchRequest) {
        return wrapWithXray(() -> delegate.deleteMessageBatch(deleteMessageBatchRequest));
    }

    @Override
    public CompletableFuture<DeleteQueueResponse> deleteQueue(final DeleteQueueRequest deleteQueueRequest) {
        return wrapWithXray(() -> delegate.deleteQueue(deleteQueueRequest));
    }

    @Override
    public CompletableFuture<DeleteQueueResponse> deleteQueue(final Consumer<DeleteQueueRequest.Builder> deleteQueueRequest) {
        return wrapWithXray(() -> delegate.deleteQueue(deleteQueueRequest));
    }

    @Override
    public CompletableFuture<GetQueueAttributesResponse> getQueueAttributes(final GetQueueAttributesRequest getQueueAttributesRequest) {
        return wrapWithXray(() -> delegate.getQueueAttributes(getQueueAttributesRequest));
    }

    @Override
    public CompletableFuture<GetQueueAttributesResponse> getQueueAttributes(final Consumer<GetQueueAttributesRequest.Builder> getQueueAttributesRequest) {
        return wrapWithXray(() -> delegate.getQueueAttributes(getQueueAttributesRequest));
    }

    @Override
    public CompletableFuture<GetQueueUrlResponse> getQueueUrl(GetQueueUrlRequest getQueueUrlRequest) {
        return wrapWithXray(() -> delegate.getQueueUrl(getQueueUrlRequest));
    }

    @Override
    public CompletableFuture<GetQueueUrlResponse> getQueueUrl(final Consumer<GetQueueUrlRequest.Builder> getQueueUrlRequest) {
        return wrapWithXray(() -> delegate.getQueueUrl(getQueueUrlRequest));
    }

    @Override
    public CompletableFuture<ListDeadLetterSourceQueuesResponse> listDeadLetterSourceQueues(
            final ListDeadLetterSourceQueuesRequest listDeadLetterSourceQueuesRequest) {
        return wrapWithXray(() -> delegate.listDeadLetterSourceQueues(listDeadLetterSourceQueuesRequest));
    }

    @Override
    public CompletableFuture<ListDeadLetterSourceQueuesResponse> listDeadLetterSourceQueues(
            final Consumer<ListDeadLetterSourceQueuesRequest.Builder> listDeadLetterSourceQueuesRequest) {
        return wrapWithXray(() -> delegate.listDeadLetterSourceQueues(listDeadLetterSourceQueuesRequest));
    }

    @Override
    public CompletableFuture<ListQueueTagsResponse> listQueueTags(final ListQueueTagsRequest listQueueTagsRequest) {
        return wrapWithXray(() -> delegate.listQueueTags(listQueueTagsRequest));
    }

    @Override
    public CompletableFuture<ListQueueTagsResponse> listQueueTags(final Consumer<ListQueueTagsRequest.Builder> listQueueTagsRequest) {
        return wrapWithXray(() -> delegate.listQueueTags(listQueueTagsRequest));
    }

    @Override
    public CompletableFuture<ListQueuesResponse> listQueues(final ListQueuesRequest listQueuesRequest) {
        return wrapWithXray(() -> delegate.listQueues(listQueuesRequest));
    }

    @Override
    public CompletableFuture<ListQueuesResponse> listQueues(final Consumer<ListQueuesRequest.Builder> listQueuesRequest) {
        return wrapWithXray(() -> delegate.listQueues(listQueuesRequest));
    }

    @Override
    public CompletableFuture<ListQueuesResponse> listQueues() {
        return wrapWithXray(delegate::listQueues);
    }

    @Override
    public CompletableFuture<PurgeQueueResponse> purgeQueue(final PurgeQueueRequest purgeQueueRequest) {
        return wrapWithXray(() -> delegate.purgeQueue(purgeQueueRequest));
    }

    @Override
    public CompletableFuture<PurgeQueueResponse> purgeQueue(final Consumer<PurgeQueueRequest.Builder> purgeQueueRequest) {
        return wrapWithXray(() -> delegate.purgeQueue(purgeQueueRequest));
    }

    @Override
    public CompletableFuture<ReceiveMessageResponse> receiveMessage(final ReceiveMessageRequest receiveMessageRequest) {
        return wrapWithXray(() -> delegate.receiveMessage(receiveMessageRequest));
    }

    @Override
    public CompletableFuture<ReceiveMessageResponse> receiveMessage(final Consumer<ReceiveMessageRequest.Builder> receiveMessageRequest) {
        return wrapWithXray(() -> delegate.receiveMessage(receiveMessageRequest));
    }

    @Override
    public CompletableFuture<RemovePermissionResponse> removePermission(final RemovePermissionRequest removePermissionRequest) {
        return wrapWithXray(() -> delegate.removePermission(removePermissionRequest));
    }

    @Override
    public CompletableFuture<RemovePermissionResponse> removePermission(final Consumer<RemovePermissionRequest.Builder> removePermissionRequest) {
        return wrapWithXray(() -> delegate.removePermission(removePermissionRequest));
    }

    @Override
    public CompletableFuture<SendMessageResponse> sendMessage(final SendMessageRequest sendMessageRequest) {
        return wrapWithXray(() -> delegate.sendMessage(sendMessageRequest));
    }

    @Override
    public CompletableFuture<SendMessageResponse> sendMessage(final Consumer<SendMessageRequest.Builder> sendMessageRequest) {
        return wrapWithXray(() -> delegate.sendMessage(sendMessageRequest));
    }

    @Override
    public CompletableFuture<SendMessageBatchResponse> sendMessageBatch(final SendMessageBatchRequest sendMessageBatchRequest) {
        return wrapWithXray(() -> delegate.sendMessageBatch(sendMessageBatchRequest));
    }

    @Override
    public CompletableFuture<SendMessageBatchResponse> sendMessageBatch(final Consumer<SendMessageBatchRequest.Builder> sendMessageBatchRequest) {
        return wrapWithXray(() -> delegate.sendMessageBatch(sendMessageBatchRequest));
    }

    @Override
    public CompletableFuture<SetQueueAttributesResponse> setQueueAttributes(final SetQueueAttributesRequest setQueueAttributesRequest) {
        return wrapWithXray(() -> delegate.setQueueAttributes(setQueueAttributesRequest));
    }

    @Override
    public CompletableFuture<SetQueueAttributesResponse> setQueueAttributes(final Consumer<SetQueueAttributesRequest.Builder> setQueueAttributesRequest) {
        return wrapWithXray(() -> delegate.setQueueAttributes(setQueueAttributesRequest));
    }

    @Override
    public CompletableFuture<TagQueueResponse> tagQueue(final TagQueueRequest tagQueueRequest) {
        return wrapWithXray(() -> delegate.tagQueue(tagQueueRequest));
    }

    @Override
    public CompletableFuture<TagQueueResponse> tagQueue(final Consumer<TagQueueRequest.Builder> tagQueueRequest) {
        return wrapWithXray(() -> delegate.tagQueue(tagQueueRequest));
    }

    @Override
    public CompletableFuture<UntagQueueResponse> untagQueue(final UntagQueueRequest untagQueueRequest) {
        return wrapWithXray(() -> delegate.untagQueue(untagQueueRequest));
    }

    @Override
    public CompletableFuture<UntagQueueResponse> untagQueue(final Consumer<UntagQueueRequest.Builder> untagQueueRequest) {
        return wrapWithXray(() -> delegate.untagQueue(untagQueueRequest));
    }

    /**
     * Will wrap the underlying code with a segment if one has not already been started.
     *
     * @param supplier the code to wrap with an Xray segment
     * @param <M>      the return type of the underlying code
     * @return the value returned by the underling code
     */
    private <M> M wrapWithXray(Supplier<M> supplier) {
        final Optional<Segment> optionalSegment = recorder.getCurrentSegmentOptional();
        try {
            if (!optionalSegment.isPresent()) {
                recorder.beginSegment(segmentNamingStrategy.getSegmentName());
            }
            return supplier.get();
        } finally {
            if (!optionalSegment.isPresent()) {
                recorder.endSegment();
            }
        }
    }

    @Override
    public void close() {
        delegate.close();
    }
}
