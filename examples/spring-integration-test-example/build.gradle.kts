
description = "Contains an example for writing an integration test for the SQS Listener"

plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":java-dynamic-sqs-listener-spring:java-dynamic-sqs-listener-spring-starter"))

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.elasticmq:elasticmq-rest-sqs_2.12")
    testImplementation(project(":util:elasticmq-sqs-client"))
    testImplementation(project(":util:expected-test-exception"))
}
