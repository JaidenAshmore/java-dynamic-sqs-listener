
description = "Message Processing Decorator that adds Brave Tracing to the message listeners"

dependencies {
    api(project(":java-dynamic-sqs-listener-api"))
    api("io.zipkin.brave:brave")
    implementation(project(":sqs-brave-tracing"))
    compileOnly(project(":documentation-annotations"))

    testImplementation("io.zipkin.brave:brave-tests")
    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":expected-test-exception"))
}
