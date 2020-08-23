import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "Example of using the Core library with the Kotlin DSL, this should be equivalent to the core-examples."

val braveVersion: String by project
val jacksonVersion: String by project
val logbackVersion: String by project

plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":java-dynamic-sqs-listener-core"))
    implementation(project(":elasticmq-sqs-client"))
    implementation(project(":core-kotlin-dsl"))
    implementation("io.zipkin.brave:brave:$braveVersion")
    implementation("io.zipkin.brave:brave-context-slf4j:$braveVersion")
    implementation(project(":brave-extension-core"))
    implementation("ch.qos.logback:logback-core:$logbackVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.create<JavaExec>("runApp") {
    classpath = sourceSets.main.get().runtimeClasspath

    main = "com.jashmore.sqs.examples.KotlinConcurrentBrokerExample"
}
