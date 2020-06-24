
description = "Core Spring implementation for the Java Dynamic SQS Listener library"

dependencies {
    api(project(":java-dynamic-sqs-listener-spring:java-dynamic-sqs-listener-spring-api"))
    api(project(":java-dynamic-sqs-listener-core"))
    implementation(project(":util:common-utils"))
    implementation(project(":util:annotation-utils"))
    implementation(project(":util:documentation-annotations"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework:spring-tx")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-starter-aop")
    testImplementation(project(":util:elasticmq-sqs-client"))
}
