
description = "Extension for integration AWS Xray Tracing into the Message Listeners"

dependencies {
    api("com.amazonaws:aws-xray-recorder-sdk-core")
    api(project(":java-dynamic-sqs-listener-api"))
    compileOnly(project(":documentation-annotations"))
}
