import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "Contains examples for connection to SQS in an Ktor Application"

plugins {
    kotlin("jvm") version "1.3.72"
}

repositories {
    jcenter()
}

apply(plugin = "kotlin")

dependencies {
    implementation(project(":java-dynamic-sqs-listener-core"))
    implementation(project(":core-kotlin-dsl"))
    implementation(project(":elasticmq-sqs-client"))
    api(project(":java-dynamic-sqs-listener-ktor-core"))
    implementation("io.ktor:ktor-server-netty:1.3.2")
    implementation("ch.qos.logback:logback-core")
    implementation("ch.qos.logback:logback-classic")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.create<JavaExec>("runApp") {
    classpath = sourceSets.main.get().runtimeClasspath

    main = "com.jashmore.sqs.examples.KtorApplicationExample"
}
