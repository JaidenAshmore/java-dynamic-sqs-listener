
description = "Contains examples for listening to SQS queues with tracing provided by Spring Sleuth"

plugins {
    id("org.springframework.boot")
}

val springBootVersion: String by project
val springCloudVersion: String by project

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-starter-zipkin:$springCloudVersion")
    implementation(project(":java-dynamic-sqs-listener-spring-starter"))
    implementation(project(":elasticmq-sqs-client"))
    implementation(project(":brave-extension-spring-boot"))
    implementation(project(":sqs-brave-tracing"))
}
