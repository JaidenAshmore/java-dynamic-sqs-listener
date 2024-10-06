
description = "Contains a way to attach message listeners via annotations"

dependencies {
    api(project(":java-dynamic-sqs-listener-core"))
    implementation(project(":common-utils"))
    implementation(project(":annotation-utils"))
    compileOnly(project(":documentation-annotations"))

    testImplementation(project(":elasticmq-sqs-client"))
}
