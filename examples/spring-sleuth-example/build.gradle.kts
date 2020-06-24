
description = "Contains examples for listening to SQS queues with tracing provided by Spring Sleuth"

plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-starter-zipkin:2.2.3.RELEASE")
    implementation(project(":java-dynamic-sqs-listener-spring:java-dynamic-sqs-listener-spring-starter"))
    implementation(project(":util:elasticmq-sqs-client"))
    implementation(project(":extensions:brave-message-processing-decorator"))
    implementation(project(":util:sqs-brave-tracing"))
}
