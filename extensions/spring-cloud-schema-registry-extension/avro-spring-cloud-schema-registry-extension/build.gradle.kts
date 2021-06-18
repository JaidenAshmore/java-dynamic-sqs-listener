import com.github.davidmc24.gradle.plugin.avro.GenerateAvroJavaTask

description = "Apache Avro implementation of the Spring Cloud Schema Registry Extension"

plugins {
    id("com.github.davidmc24.gradle.plugin.avro")
}

val avroVersion: String by project
val springBootVersion: String by project

dependencies {
    api("org.apache.avro:avro:$avroVersion")
    api(project(":spring-cloud-schema-registry-extension-api"))

    compileOnly(project(":documentation-annotations"))

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":avro-spring-cloud-schema-registry-sqs-client"))
    testImplementation(project(":java-dynamic-sqs-listener-spring-starter"))
    testImplementation(project(":in-memory-spring-cloud-schema-registry"))
}

val generateAvro = tasks.register<GenerateAvroJavaTask>("generateAvro") {
    setSource("src/test/resources/avro-test-schemas")
    setOutputDir(file("${buildDir}/generated-test-sources/avro"))
}

tasks.named<JavaCompile>("compileJava").configure {
    source(generateAvro)
}

sourceSets {
    test {
        java {
            srcDir("${buildDir}/generated-test-sources/avro")
        }
    }
}
