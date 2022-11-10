description = "This contains the api functionality for the SQS Listener. This can be used for consumer to implement themselves"

val awsVersion: String by project

dependencies {
    api(platform("software.amazon.awssdk:bom:${awsVersion}"))
    api("software.amazon.awssdk:sqs")
    api("software.amazon.awssdk:sts")
    compileOnly(project(":documentation-annotations"))
}
