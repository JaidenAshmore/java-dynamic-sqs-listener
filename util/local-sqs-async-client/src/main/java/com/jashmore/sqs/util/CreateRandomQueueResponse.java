package com.jashmore.sqs.util;

import lombok.Builder;
import lombok.Value;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;

@Value
@Builder
public class CreateRandomQueueResponse {
    CreateQueueResponse response;
    String queueName;

    public String queueUrl() {
        return response.queueUrl();
    }
}
