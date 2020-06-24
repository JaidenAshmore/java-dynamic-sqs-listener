
description = "Contains examples for using the Spring Starter implementation of the framework."

plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":java-dynamic-sqs-listener-spring:java-dynamic-sqs-listener-spring-starter"))
    implementation("org.elasticmq:elasticmq-rest-sqs_2.12")
    implementation(project(":util:local-sqs-async-client"))
    implementation("org.springframework.cloud:spring-cloud-aws-messaging:2.1.0.RELEASE")
    implementation("org.springframework:spring-jms:5.2.7.RELEASE")
    implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.4")
}
