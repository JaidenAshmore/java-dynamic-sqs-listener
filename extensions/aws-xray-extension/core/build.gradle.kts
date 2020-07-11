
description = "Extension for integration AWS Xray into Message Listeners"

dependencies {
    api("com.amazonaws:aws-xray-recorder-sdk-core")
    api(project(":java-dynamic-sqs-listener-api"))
    compileOnly(project(":documentation-annotations"))
}
