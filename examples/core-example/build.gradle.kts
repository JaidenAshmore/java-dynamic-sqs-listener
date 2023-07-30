
description = "Contains examples for using the core implementation of the listener with plan Java objects."

val braveVersion: String by project
val logbackVersion: String by project

dependencies {
    implementation("com.google.guava:guava:29.0-jre")
    implementation(project(":java-dynamic-sqs-listener-core"))
    implementation(project(":elasticmq-sqs-client"))
    implementation("io.zipkin.brave:brave:$braveVersion")
    implementation("io.zipkin.brave:brave-context-slf4j:$braveVersion")
    implementation(project(":brave-extension-core"))
    compileOnly(project(":documentation-annotations"))
    implementation("ch.qos.logback:logback-core:$logbackVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
}

tasks.create<JavaExec>("runApp") {
    classpath = sourceSets.main.get().runtimeClasspath

    main = "com.jashmore.sqs.examples.ConcurrentBrokerExample"
}
