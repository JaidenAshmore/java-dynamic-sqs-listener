description = "This contains the api functionality for the SQS Listener. This can be used for consumer to implement themselves"

dependencies {
    api("software.amazon.awssdk:sqs")
    compileOnly(project(":documentation-annotations"))
}
