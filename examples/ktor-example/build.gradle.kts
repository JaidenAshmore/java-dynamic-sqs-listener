
description = "Contains examples for connection to SQS in an Ktor Application"

dependencies {
    implementation(project(":java-dynamic-sqs-listener-core"))
    implementation(project(":core-kotlin-dsl"))
    implementation(project(":elasticmq-sqs-client"))
    api(project(":java-dynamic-sqs-listener-ktor-core"))
    implementation("io.ktor:ktor-server-netty:1.3.2")
    implementation("ch.qos.logback:logback-core")
    implementation("ch.qos.logback:logback-classic")
}

tasks.create<JavaExec>("runApp") {
    classpath = sourceSets.main.get().runtimeClasspath

    main = "com.jashmore.sqs.examples.KtorApplicationExample"
}
