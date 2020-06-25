
description = "Message Processing Decorator that adds Brave Tracing to the message listeners"

java {
    registerFeature("spring") {
        usingSourceSet(sourceSets.main.get())
    }
}

dependencies {
    api(project(":java-dynamic-sqs-listener-api"))
    api("io.zipkin.brave:brave")
    implementation(project(":sqs-brave-tracing"))
    compileOnly(project(":documentation-annotations"))

    "springImplementation"("org.springframework:spring-context")
    "springImplementation"("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation("io.zipkin.brave:brave-tests")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":java-dynamic-sqs-listener-spring-starter"))
    testImplementation(project(":expected-test-exception"))
}
