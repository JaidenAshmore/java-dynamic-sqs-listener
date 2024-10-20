
description = "Extension for integration AWS Xray Tracing into the Message Listeners"

val awsXrayVersion: String by project

dependencies {
    api("com.amazonaws:aws-xray-recorder-sdk-core:$awsXrayVersion")
    api(project(":java-dynamic-sqs-listener-api"))
    implementation(project(":common-utils"))
    compileOnly(project(":documentation-annotations"))

    testImplementation("org.checkerframework:checker-qual:3.7.1")
    testImplementation(project(":java-dynamic-sqs-listener-core"))
    testImplementation(project(":expected-test-exception"))
}

configurations.all {
    resolutionStrategy.eachDependency {
        // xray requires an older version of jackson
        if (requested.group.startsWith("com.fasterxml.jackson")) {
            useVersion("2.15.2")
        }
    }
}
