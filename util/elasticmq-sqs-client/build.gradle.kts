
description = "Provides the ability to create a SqsAsyncClient backed by an in-memory ElasticMQ SQS Server"

dependencies {
    api("software.amazon.awssdk:sqs")
    api(project(":util:local-sqs-async-client"))
    implementation("org.elasticmq:elasticmq-rest-sqs_2.12")
}
