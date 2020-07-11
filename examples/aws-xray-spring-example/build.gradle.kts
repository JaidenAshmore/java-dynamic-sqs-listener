
description = "Contains examples for using the AWS XRay tracing."

plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation("software.amazon.awssdk:sns")
    implementation("com.amazonaws:aws-xray-recorder-sdk-spring")
    implementation("com.amazonaws:aws-xray-recorder-sdk-aws-sdk-v2-instrumentor")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":aws-xray-extension-spring-boot"))
    implementation(project(":java-dynamic-sqs-listener-spring-starter"))
}
