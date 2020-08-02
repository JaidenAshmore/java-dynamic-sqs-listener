
description = "Wrapper around the AWS SQS Async Client to help serialize messages using Avro and the Spring Cloud Schema Registry"

val avroVersion: String by project
val awsVersion: String by project
val springCloudSchemaRegistryVersion: String by project

dependencies {
    api(platform("software.amazon.awssdk:bom:$awsVersion"))
    api("software.amazon.awssdk:sqs")
    api("org.springframework.cloud:spring-cloud-schema-registry-client:$springCloudSchemaRegistryVersion")
    api("org.apache.avro:avro:$avroVersion")
}
