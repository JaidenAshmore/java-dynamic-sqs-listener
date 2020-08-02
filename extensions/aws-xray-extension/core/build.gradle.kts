
description = "Extension for integration AWS Xray Tracing into the Message Listeners"

val awsXrayVersion: String by project

dependencies {
    api("com.amazonaws:aws-xray-recorder-sdk-core:$awsXrayVersion")
    api(project(":java-dynamic-sqs-listener-api"))
    implementation(project(":common-utils"))
    compileOnly(project(":documentation-annotations"))

    testImplementation(project(":java-dynamic-sqs-listener-core"))
    testImplementation(project(":expected-test-exception"))
}
