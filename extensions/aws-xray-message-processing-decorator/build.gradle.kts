
description = "Message Processing Decorator that wraps message processing in an AWS XRay Segment"

java {
    registerFeature("spring") {
        usingSourceSet(sourceSets.main.get())
    }
}

dependencies {
    api("com.amazonaws:aws-xray-recorder-sdk-core")
    api(project(":java-dynamic-sqs-listener-api"))
    compileOnly(project(":documentation-annotations"))

    "springImplementation"("org.springframework:spring-context")
    "springImplementation"("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
}
