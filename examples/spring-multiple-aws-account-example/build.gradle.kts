
description = "Java Dynamic SQS Listener - Spring Starter - Multiple AWS Accounts Example"

plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":java-dynamic-sqs-listener-spring-starter"))
    implementation(project(":local-sqs-async-client"))
    implementation("org.elasticmq:elasticmq-rest-sqs_2.12")
}
