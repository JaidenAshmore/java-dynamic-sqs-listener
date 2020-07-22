
description = "Contains examples for using the Spring Starter implementation of the framework."

plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":java-dynamic-sqs-listener-spring-starter"))
    implementation("org.elasticmq:elasticmq-rest-sqs_2.12")
    implementation(project(":local-sqs-async-client"))
}
