# How to Connect to an AWS SQS Queue

While most of the examples provided in this library use [ElasticMQ](https://github.com/adamw/elasticmq) as the SQS queue, the real application should be
using an actual SQS queue. This provides a very high level, generic guide to creating a Queue in AWS which should help you get started. However, the
online documentation provided by Amazon would be a better resources for learning about SQS queues, for example I used
[AWS Documentation - SQS Setting Up](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-setting-up.html) myself to
write this guide.

In this example we are defining the SQS Properties needed via Environment variables in the application. See `SqsAsyncClient#create()` for more thorough
documentation about different ways to define these properties. The [Spring AWS Example](../../examples/spring-aws-example) will be
used for this guide.

## Usage

See the [Spring AWS Example README.md](../../examples/spring-aws-example/README.md) for steps to use this yourself.
