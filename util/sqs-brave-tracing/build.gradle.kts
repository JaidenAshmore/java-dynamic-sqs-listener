
description = "Helper Util for adding Tracing information to the message attributes of outbound SQS messages"

val awsVersion: String by project
val braveVersion: String by project

dependencies {
    api(platform("software.amazon.awssdk:bom:$awsVersion"))
    implementation("software.amazon.awssdk:sqs")
    api("io.zipkin.brave:brave:$braveVersion")

    testImplementation("io.zipkin.brave:brave-tests:$braveVersion")
    testImplementation(project(":elasticmq-sqs-client"))
}
