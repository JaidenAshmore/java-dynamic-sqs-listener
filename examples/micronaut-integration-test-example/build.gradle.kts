
description = "Contains an example for writing an integration test for the SQS Listener"

plugins {
    id("io.micronaut.minimal.application") version "4.4.2"
}

val micronautVersion: String by project

dependencies {
    implementation("io.micronaut:micronaut-http-server-netty")
    runtimeOnly("ch.qos.logback:logback-classic")
    implementation(project(":java-dynamic-sqs-listener-micronaut-core"))

    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":expected-test-exception"))
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
}
