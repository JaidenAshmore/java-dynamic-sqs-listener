
description = "Message Processing Decorator that adds Brave Tracing to the message listeners"

java {
    registerFeature("spring") {
        usingSourceSet(sourceSets.main.get())
    }
}

dependencies {
    api(project(":java-dynamic-sqs-listener-api"))
    api("io.zipkin.brave:brave")
    implementation(project(":util:sqs-brave-tracing"))
    implementation(project(":util:documentation-annotations"))

    "springImplementation"("org.springframework:spring-context:5.2.7.RELEASE")
    "springImplementation"("org.springframework.boot:spring-boot-autoconfigure:2.3.1.RELEASE")

    testImplementation("io.zipkin.brave:brave-tests")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation(project(":util:elasticmq-sqs-client"))
    testImplementation(project(":java-dynamic-sqs-listener-spring:java-dynamic-sqs-listener-spring-starter"))
    testImplementation(project(":util:expected-test-exception"))
}
