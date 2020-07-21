import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "Kotlin DSL for building the Core message listeners"

plugins {
    kotlin("jvm") version "1.3.72"
}

apply(plugin = "kotlin")

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api(project(":java-dynamic-sqs-listener-core"))

    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":expected-test-exception"))
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}
