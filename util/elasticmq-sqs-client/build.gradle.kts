
description = "Provides the ability to create a SqsAsyncClient backed by an in-memory ElasticMQ SQS Server"

val elasticMqVersion: String by project

dependencies {
    api(project(":local-sqs-async-client"))
    implementation("org.elasticmq:elasticmq-rest-sqs_2.12:$elasticMqVersion")
}
