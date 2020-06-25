
description = "Provides a local Amazon SQS implementation that can talk to a locally running SQS queue like localstack"

dependencies {
    implementation("software.amazon.awssdk:sqs")
    implementation("org.slf4j:slf4j-api")
    implementation(project(":common-utils"))

    testImplementation("org.elasticmq:elasticmq-rest-sqs_2.12")
}
