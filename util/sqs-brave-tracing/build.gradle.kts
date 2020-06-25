
description = "Helper Util for adding Tracing information to the message attributes of outbound SQS messages"

dependencies {
    api("io.zipkin.brave:brave")
    implementation("software.amazon.awssdk:sqs")

    testImplementation("io.zipkin.brave:brave-tests")
    testImplementation(project(":elasticmq-sqs-client"))
}
