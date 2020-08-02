
description = "Contains examples for connection to SQS in an Ktor Application"

val logbackVersion: String by project
val ktorVersion: String by project

dependencies {
    implementation(project(":java-dynamic-sqs-listener-core"))
    implementation(project(":core-kotlin-dsl"))
    implementation(project(":elasticmq-sqs-client"))
    api(project(":java-dynamic-sqs-listener-ktor-core"))
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("ch.qos.logback:logback-core:$logbackVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
}

tasks.create<JavaExec>("runApp") {
    classpath = sourceSets.main.get().runtimeClasspath

    main = "com.jashmore.sqs.examples.KtorApplicationExample"
}
