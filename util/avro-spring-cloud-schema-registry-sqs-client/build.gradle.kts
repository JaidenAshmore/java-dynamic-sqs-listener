
description = "Wrapper around the AWS SQS Async Client to help serialize messages using Avro and the Spring Cloud Schema Registry"

dependencies {
    api("software.amazon.awssdk:sqs")
    api("org.springframework.cloud:spring-cloud-schema-registry-client")
    api("org.apache.avro:avro")
}
