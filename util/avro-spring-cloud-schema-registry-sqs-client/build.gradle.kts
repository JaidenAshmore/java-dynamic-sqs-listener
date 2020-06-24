
description = "Wrapper around the AWS SQS Async Client to help serialize messages using Avro and the Spring Cloud Schema Registry"

dependencies {
    api("software.amazon.awssdk:sqs")
    api("org.springframework.cloud:spring-cloud-schema-registry-client:1.0.3.RELEASE")
    api("org.apache.avro:avro:1.9.2")
}
