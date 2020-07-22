# Spring AWS Example

An example that uses the [spring starter](../../spring/spring-starter) module to listen to an actual queue on AWS.

## Steps

1. Create a new AWS account. You can Google for links on where to do this.
1. Create a new SQS Queue in a region near you, e.g. us-east-2 (Ohio). You will need the region and Queue URL.
1. Create a new IAM user that has full permission (for simplicity, otherwise you can put a more restricted permissions) to this SQS queue. You will
need the Access Key ID and Secret Access Key.
1. Change directory to the AWS Spring example.

    ```bash
    cd examples/java-dynamic-sqs-listener-spring-aws-example
    ```

1. Run the Spring Boot application with the AWS details recorded above. For example:

    ```bash
    AWS_ACCESS_KEY_ID={KEY_RECORDED_ABOVE} \
    AWS_REGION={REGION_QUEUE_CREATED_IN_ABOVE} \
    AWS_SECRET_ACCESS_KEY={SECRET_KEY_RECORDED_ABOVE} \
    SQS_QUEUE_URL={FULL_URL_OF_SQS_QUEUE} \
    gradle bootRun
    ```

1. Send a message to the Queue by right clicking the queue in the AWS Console and selecting Send Message
