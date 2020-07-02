# How to Connect to an AWS SQS Queue

While most of the examples provided in this library are shown using a local [ElasticMQ](https://github.com/adamw/elasticmq) or
[Localstack](https://github.com/localstack/localstack) for a mock SQS queue, the real application should be using an actual SQS queue. This provides a very
high level, generic guide to creating a Queue in AWS which should help you getting started. However, the online documentation provided by Amazon would be a
better resources for learning about SQS queues, for example I used
[AWS Documentation - SQS Setting Up](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-setting-up.html) myself to
write this guide.

In this example we are defining the SQS Properties needed via Environment variables in the application. See `SqsAsyncClient#create()` for more thorough
documentation about different ways to define these properties. The [Spring AWS Example](../../examples/spring-aws-example) will be
used for this guide.

## Steps

1. Create a new AWS account. You can Google for links on where to do this.
1. Create a new SQS Queue in a region near you, e.g. us-east-2 (Ohio). You will need the region and Queue URL.
1. Create a new IAM user that has full permission to this SQS queue. You will need the Access Key ID and Secret Access Key.
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
