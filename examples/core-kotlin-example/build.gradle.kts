
description = "Example of using the Core library with the Kotlin DSL, this should be equivalent to the core-examples."

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":java-dynamic-sqs-listener-core"))
    implementation(project(":elasticmq-sqs-client"))
    implementation(project(":core-kotlin-dsl"))
    implementation("io.zipkin.brave:brave")
    implementation("io.zipkin.brave:brave-context-slf4j")
    implementation(project(":brave-extension-core"))
    implementation("ch.qos.logback:logback-core")
    implementation("ch.qos.logback:logback-classic")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

tasks.create<JavaExec>("runApp") {
    classpath = sourceSets.main.get().runtimeClasspath

    main = "com.jashmore.sqs.examples.KotlinConcurrentBrokerExample"
}
