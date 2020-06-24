description = "This contains the api functionality for the SQS Listener. This can be used for consumer to implement themselves"

dependencies {
    api("software.amazon.awssdk:sqs:2.13.7")
    implementation(project(":util:documentation-annotations"))
}
