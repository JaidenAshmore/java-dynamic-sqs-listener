
description = "Core Spring implementation for the Java Dynamic SQS Listener library"

val springBootVersion: String by project

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

    api(project(":java-dynamic-sqs-listener-spring-api"))
    api(project(":java-dynamic-sqs-listener-core"))
    implementation(project(":common-utils"))
    implementation(project(":annotation-utils"))
    compileOnly(project(":documentation-annotations"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework:spring-tx")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-starter-aop")
    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":expected-test-exception"))
}
