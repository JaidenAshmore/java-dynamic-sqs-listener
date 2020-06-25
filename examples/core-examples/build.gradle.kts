
description = "Contains examples for using the core implementation of the listener with plan Java objects."

dependencies {
    implementation("com.google.guava:guava:29.0-jre")
    implementation(project(":java-dynamic-sqs-listener-core"))
    implementation(project(":elasticmq-sqs-client"))
    implementation("io.zipkin.brave:brave")
    implementation("io.zipkin.brave:brave-context-slf4j")
    implementation(project(":brave-message-processing-decorator"))
    compileOnly(project(":documentation-annotations"))
}
