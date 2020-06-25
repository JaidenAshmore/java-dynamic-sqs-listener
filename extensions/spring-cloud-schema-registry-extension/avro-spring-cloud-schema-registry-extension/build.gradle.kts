import com.commercehub.gradle.plugin.avro.GenerateAvroJavaTask

description = "Apache Avro implementation of the Spring Cloud Schema Registry Extension"

plugins {
    id("com.commercehub.gradle.plugin.avro-base")
}

java {
    registerFeature("spring") {
        usingSourceSet(sourceSets.main.get())
    }
}

dependencies {
    api("org.apache.avro:avro:1.9.2")
    api(project(":spring-cloud-schema-registry-extension-api"))

    "springImplementation"("org.springframework:spring-core")
    "springImplementation"("org.springframework:spring-context")
    "springImplementation"("org.springframework.boot:spring-boot-autoconfigure")
    "springImplementation"("org.springframework.boot:spring-boot-configuration-processor")

    compileOnly(project(":documentation-annotations"))

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
