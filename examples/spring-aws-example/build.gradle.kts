
description = "Contains examples for connecting to an actual AWS SQS Queue"

plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":java-dynamic-sqs-listener-spring:java-dynamic-sqs-listener-spring-starter"))
    implementation("org.elasticmq:elasticmq-rest-sqs_2.12")
    implementation(project(":util:local-sqs-async-client"))
}
