
description = "In Memory implementation of the Spring Cloud Schema Registry that is used for testing purposes"

val springCloudSchemaRegistryVersion: String by project

dependencies {
    compileOnly(project(":documentation-annotations"))
    implementation("org.springframework.cloud:spring-cloud-schema-registry-client:$springCloudSchemaRegistryVersion")
}
