
description = "Contains the core functionality for the library"

val immutablesVersion: String by project
val jacksonVersion: String by project
val slf4jVersion: String by project

dependencies {
    // External dependencies
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    annotationProcessor("org.immutables:value:$immutablesVersion")
    api("org.immutables:value-annotations:$immutablesVersion")

    api(project(":java-dynamic-sqs-listener-api"))
    implementation(project(":annotation-utils"))
    compileOnly(project(":documentation-annotations"))
    implementation(project(":common-utils"))

    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":proxy-method-interceptor"))
    testImplementation(project(":expected-test-exception"))
    testCompileOnly(project(":documentation-annotations"))
}
