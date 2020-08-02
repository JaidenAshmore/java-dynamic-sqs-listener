
description = "Provides a local Amazon SQS implementation that can talk to a locally running SQS queue like localstack"

val awsVersion: String by project
val slf4jVersion: String by project
val elasticMqVersion: String by project

dependencies {
    api(platform("software.amazon.awssdk:bom:$awsVersion"))
    api("software.amazon.awssdk:sqs")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation(project(":common-utils"))

    testImplementation("org.elasticmq:elasticmq-rest-sqs_2.12:$elasticMqVersion")
}
