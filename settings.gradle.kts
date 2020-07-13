
rootProject.name = "java-dynamic-sqs-listener-parent"

pluginManagement {
    plugins {
        id("org.springframework.boot") version "2.2.2.RELEASE"
        id("com.commercehub.gradle.plugin.avro-base") version "0.21.0"
    }
    repositories {
        gradlePluginPortal()
        maven (url="https://dl.bintray.com/gradle/gradle-plugins")
    }
}

include(
        // Core
        ":java-dynamic-sqs-listener-api",
        ":java-dynamic-sqs-listener-core",
        
        // Spring
        ":java-dynamic-sqs-listener-spring-api",
        ":java-dynamic-sqs-listener-spring-core",
        ":java-dynamic-sqs-listener-spring-starter",
        
        // Extensions
        ":aws-xray-extension-core",
        ":aws-xray-extension-spring-boot",
        ":brave-message-processing-decorator",

        // - Spring Cloud Scheme Registry Extension
        ":spring-cloud-schema-registry-extension-api",
        ":avro-spring-cloud-schema-registry-extension",
        ":in-memory-spring-cloud-schema-registry",
        
        // Utils
        ":avro-spring-cloud-schema-registry-sqs-client",
        ":common-utils",
        ":elasticmq-sqs-client",
        ":expected-test-exception",
        ":local-sqs-async-client",
        ":proxy-method-interceptor",
        ":sqs-brave-tracing",
        ":annotation-utils",
        ":documentation-annotations",

        // Examples
        ":aws-xray-spring-example",
        ":java-dynamic-sqs-listener-core-examples",
        ":java-dynamic-sqs-listener-spring-aws-example",
        ":spring-cloud-schema-registry-consumer",
        ":spring-cloud-schema-registry-producer",
        ":spring-cloud-schema-registry-producer-two",
        ":spring-cloud-schema-registry-example",
        ":java-dynamic-sqs-listener-spring-integration-test-example",
        ":multiple-aws-account-example",
        ":spring-sleuth-example",
        ":java-dynamic-sqs-listener-spring-starter-examples",
        ":sqs-listener-library-comparison"
)

project(":java-dynamic-sqs-listener-api").projectDir = file("api")
project(":java-dynamic-sqs-listener-core").projectDir = file("core")
project(":aws-xray-spring-example").projectDir = file("examples/aws-xray-spring-example")
project(":java-dynamic-sqs-listener-core-examples").projectDir = file("examples/core-examples")
project(":java-dynamic-sqs-listener-spring-aws-example").projectDir = file("examples/spring-aws-example")
project(":spring-cloud-schema-registry-consumer").projectDir = file("examples/spring-cloud-schema-registry-example/spring-cloud-schema-registry-consumer")
project(":spring-cloud-schema-registry-producer").projectDir = file("examples/spring-cloud-schema-registry-example/spring-cloud-schema-registry-producer")
project(":spring-cloud-schema-registry-producer-two").projectDir = file("examples/spring-cloud-schema-registry-example/spring-cloud-schema-registry-producer-two")
project(":spring-cloud-schema-registry-example").projectDir = file("examples/spring-cloud-schema-registry-example")
project(":java-dynamic-sqs-listener-spring-integration-test-example").projectDir = file("examples/spring-integration-test-example")
project(":multiple-aws-account-example").projectDir = file("examples/spring-multiple-aws-account-example")
project(":spring-sleuth-example").projectDir = file("examples/spring-sleuth-example")
project(":java-dynamic-sqs-listener-spring-starter-examples").projectDir = file("examples/spring-starter-examples")
project(":sqs-listener-library-comparison").projectDir = file("examples/sqs-listener-library-comparison")
project(":aws-xray-extension-core").projectDir = file("extensions/aws-xray-extension/core")
project(":aws-xray-extension-spring-boot").projectDir = file("extensions/aws-xray-extension/spring-boot")
project(":brave-message-processing-decorator").projectDir = file("extensions/brave-message-processing-decorator")
project(":spring-cloud-schema-registry-extension-api").projectDir = file("extensions/spring-cloud-schema-registry-extension/spring-cloud-schema-registry-extension-api")
project(":avro-spring-cloud-schema-registry-extension").projectDir = file("extensions/spring-cloud-schema-registry-extension/avro-spring-cloud-schema-registry-extension")
project(":in-memory-spring-cloud-schema-registry").projectDir = file("extensions/spring-cloud-schema-registry-extension/in-memory-spring-cloud-schema-registry")
project(":java-dynamic-sqs-listener-spring-api").projectDir = file("spring/spring-api")
project(":java-dynamic-sqs-listener-spring-core").projectDir = file("spring/spring-core")
project(":java-dynamic-sqs-listener-spring-starter").projectDir = file("spring/spring-starter")
project(":avro-spring-cloud-schema-registry-sqs-client").projectDir = file("util/avro-spring-cloud-schema-registry-sqs-client")
project(":common-utils").projectDir = file("util/common-utils")
project(":elasticmq-sqs-client").projectDir = file("util/elasticmq-sqs-client")
project(":expected-test-exception").projectDir = file("util/expected-test-exception")
project(":local-sqs-async-client").projectDir = file("util/local-sqs-async-client")
project(":proxy-method-interceptor").projectDir = file("util/proxy-method-interceptor")
project(":sqs-brave-tracing").projectDir = file("util/sqs-brave-tracing")
project(":annotation-utils").projectDir = file("util/annotation-utils")
project(":documentation-annotations").projectDir = file("util/documentation-annotations")
