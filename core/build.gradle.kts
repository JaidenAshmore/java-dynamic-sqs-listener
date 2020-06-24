
description = "Contains the core functionality for the library"

dependencies {
    api(project(":java-dynamic-sqs-listener-api"))
    implementation(project(":util:annotation-utils"))
    compileOnly(project(":util:documentation-annotations"))
    implementation(project(":util:common-utils"))
    implementation("org.slf4j:slf4j-api:1.7.30")
    api("com.fasterxml.jackson.core:jackson-databind:2.11.0")

    testImplementation(project(":util:elasticmq-sqs-client"))
    testImplementation(project(":util:proxy-method-interceptor"))
    testImplementation(project(":util:expected-test-exception"))
    testCompileOnly(project(":util:documentation-annotations"))
}
