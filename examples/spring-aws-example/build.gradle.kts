
description = "Contains examples for connecting to an actual AWS SQS Queue"

plugins {
    id("org.springframework.boot")
}

val springBootVersion: String by project

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":java-dynamic-sqs-listener-spring-starter"))
}
