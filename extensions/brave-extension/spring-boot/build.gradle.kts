
description = "Message Processing Decorator that adds Brave Tracing to the message listeners in a Spring Boot Application"

dependencies {
    api(project(":brave-extension-core"))
    compileOnly(project(":documentation-annotations"))

    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation(project(":sqs-brave-tracing"))
    testImplementation("io.zipkin.brave:brave-tests")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":java-dynamic-sqs-listener-spring-starter"))
    testImplementation(project(":expected-test-exception"))
}
