
description = "Contains the core functionality for the library"

val jacksonVersion: String by project
val slf4jVersion: String by project

dependencies {
    api(project(":java-dynamic-sqs-listener-api"))
    implementation(project(":annotation-utils"))
    compileOnly(project(":documentation-annotations"))
    implementation(project(":common-utils"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":proxy-method-interceptor"))
    testImplementation(project(":expected-test-exception"))
    testCompileOnly(project(":documentation-annotations"))
}
