
description = "Contains an example for writing an integration test for the SQS Listener"

plugins {
    id("org.springframework.boot")
}

val springBootVersion: String by project

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":java-dynamic-sqs-listener-spring-starter"))

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":expected-test-exception"))
}
