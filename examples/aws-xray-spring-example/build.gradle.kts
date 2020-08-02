
description = "Contains examples for using the AWS XRay tracing."

plugins {
    id("org.springframework.boot")
}

val awsVersion: String by project
val awsXrayVersion: String by project
val springBootVersion: String by project

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    api(platform("software.amazon.awssdk:bom:$awsVersion"))
    implementation("software.amazon.awssdk:sns")
    implementation("com.amazonaws:aws-xray-recorder-sdk-core:$awsXrayVersion")
    implementation("com.amazonaws:aws-xray-recorder-sdk-aws-sdk-v2-instrumentor:$awsXrayVersion")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":aws-xray-extension-spring-boot"))
    implementation(project(":java-dynamic-sqs-listener-spring-starter"))
}
