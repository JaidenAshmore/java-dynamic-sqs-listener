
description = "Contains examples for using the Spring Starter implementation of the framework."

plugins {
    id("org.springframework.boot")
}

val elasticMqVersion: String by project
val springBootVersion: String by project

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":java-dynamic-sqs-listener-spring-starter"))
    implementation("org.elasticmq:elasticmq-rest-sqs_2.12:$elasticMqVersion")
    implementation(project(":local-sqs-async-client"))
}
