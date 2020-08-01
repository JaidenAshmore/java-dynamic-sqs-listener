
description = "Library for integrating the Java Dynamic Sqs Listener in a Ktor application"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api(project(":java-dynamic-sqs-listener-core"))
    api(project(":core-kotlin-dsl"))
    implementation("io.ktor:ktor-server-core:1.3.2")

    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":expected-test-exception"))
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("io.ktor:ktor-server-test-host:1.3.2")
}
