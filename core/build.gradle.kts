
description = "Contains the core functionality for the library"

dependencies {
    api(project(":java-dynamic-sqs-listener-api"))
    implementation(project(":annotation-utils"))
    compileOnly(project(":documentation-annotations"))
    implementation(project(":common-utils"))
    implementation("org.slf4j:slf4j-api:1.7.30")
    api("com.fasterxml.jackson.core:jackson-databind:2.11.0")

    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":proxy-method-interceptor"))
    testImplementation(project(":expected-test-exception"))
    testCompileOnly(project(":documentation-annotations"))
}
