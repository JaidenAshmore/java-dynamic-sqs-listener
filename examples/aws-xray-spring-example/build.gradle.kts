
description = "Contains examples for using the AWS XRay tracing."

plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation("com.amazonaws:aws-xray-recorder-sdk-spring")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":extensions:aws-xray-message-processing-decorator"))
    implementation(project(":java-dynamic-sqs-listener-spring:java-dynamic-sqs-listener-spring-starter"))
    implementation(project(":util:elasticmq-sqs-client"))
    implementation(project(":util:documentation-annotations"))
}
