description = "This contains the api functionality for the SQS Listener. This can be used for consumer to implement themselves"

val awsVersion: String by project

dependencies {
    api(platform("software.amazon.awssdk:bom:${awsVersion}"))
    api(platform("software.amazon.awssdk:sts:${awsVersion}"))
    api("software.amazon.awssdk:sqs")
    compileOnly(project(":documentation-annotations"))
}
