
description = "API for building a payload parser that has been serialized via the schema registered in the Spring Cloud Schema Registry"

dependencies {
    api(project(":java-dynamic-sqs-listener-api"))
    api("org.springframework.cloud:spring-cloud-schema-registry-client:1.0.3.RELEASE") {
        exclude(group = "org.apache.avro", module = "avro")
    }

    implementation(project(":util:annotation-utils"))
    implementation(project(":util:documentation-annotations"))
}
