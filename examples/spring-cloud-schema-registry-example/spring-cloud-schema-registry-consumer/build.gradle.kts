import com.commercehub.gradle.plugin.avro.GenerateAvroJavaTask

description = "Contains an example of a consumer deserializing a message payload that is in a schema registered in the Spring Cloud Schema Registry"

plugins {
    id("org.springframework.boot")
    id("com.commercehub.gradle.plugin.avro-base")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-autoconfigure-processor")

    implementation(project(":java-dynamic-sqs-listener-spring:java-dynamic-sqs-listener-spring-starter"))
    implementation(project(":util:local-sqs-async-client"))

    implementation(project(":extensions:spring-cloud-schema-registry-extension:avro-spring-cloud-schema-registry-extension"))
}

val generateAvro = tasks.register<GenerateAvroJavaTask>("generateAvro") {
    setSource("src/main/resources/avro")
    setOutputDir(file("build/generated/sources/avro"))
}

tasks.named<JavaCompile>("compileJava").configure {
    source(generateAvro)
}
