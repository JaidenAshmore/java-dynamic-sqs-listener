
description = "Message Processing Decorator that adds Brave Tracing to the message listeners"

val braveVersion: String by project

dependencies {
    api(project(":java-dynamic-sqs-listener-api"))
    api("io.zipkin.brave:brave:$braveVersion")
    implementation(project(":sqs-brave-tracing"))
    compileOnly(project(":documentation-annotations"))

    testImplementation("io.zipkin.brave:brave-tests:$braveVersion")
    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":expected-test-exception"))
}
