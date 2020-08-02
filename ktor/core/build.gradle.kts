
description = "Library for integrating the Java Dynamic Sqs Listener in a Ktor application"

val ktorVersion: String by project
val mockitoKotlinVersion: String by project

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api(project(":java-dynamic-sqs-listener-core"))
    api(project(":core-kotlin-dsl"))
    implementation("io.ktor:ktor-server-core:$ktorVersion")

    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":expected-test-exception"))
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
