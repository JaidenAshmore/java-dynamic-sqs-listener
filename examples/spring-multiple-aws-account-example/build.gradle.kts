
description = "Java Dynamic SQS Listener - Spring Starter - Multiple AWS Accounts Example"

plugins {
    id("org.springframework.boot")
}

val springBootVersion: String by project

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":java-dynamic-sqs-listener-spring-starter"))
    implementation(project(":elasticmq-sqs-client"))
}
