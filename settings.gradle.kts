
rootProject.name = "java-dynamic-sqs-listener-parent"

pluginManagement {
    plugins {
        id("org.springframework.boot") version "2.3.4.RELEASE"
        id("com.commercehub.gradle.plugin.avro-base") version "0.21.0"
        id("org.jlleitschuh.gradle.ktlint") version "9.4.1"
        id("com.github.spotbugs") version "4.5.1"
        kotlin("jvm") version "1.4.0"
        id("io.gitlab.arturbosch.detekt") version "1.11.2"
        id("com.jashmore.gradle.github.release") version "0.0.2"
    }
    repositories {
        gradlePluginPortal()
        jcenter()
        mavenLocal()
        maven(url = "https://dl.bintray.com/gradle/gradle-plugins")
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

    // Ktor
    ":java-dynamic-sqs-listener-ktor-core",

    // Extensions
    ":aws-xray-extension-core",
    ":aws-xray-extension-spring-boot",
    ":brave-extension-core",
    ":brave-extension-spring-boot",
    ":core-kotlin-dsl",

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
    ":example:auto-visibility-extender-example",
    ":example:aws-xray-spring-example",
    ":example:core-example",
    ":example:spring-aws-example",
    ":example:core-kotlin-example",
    ":example:fifo-example",
    ":example:ktor-example",
    ":example:spring-cloud-schema-registry:consumer",
    ":example:spring-cloud-schema-registry:producer",
    ":example:spring-cloud-schema-registry:producer-two",
    ":example:spring-integration-test-example",
    ":example:spring-multiple-aws-account-example",
    ":example:spring-sleuth-example",
    ":example:spring-starter-example",
    ":example:spring-starter-minimal-example",
    ":example:sqs-listener-library-comparison"
)

// Core
project(":java-dynamic-sqs-listener-api").projectDir = file("api")
project(":java-dynamic-sqs-listener-core").projectDir = file("core")

// Spring
project(":java-dynamic-sqs-listener-spring-api").projectDir = file("spring/spring-api")
project(":java-dynamic-sqs-listener-spring-core").projectDir = file("spring/spring-core")
project(":java-dynamic-sqs-listener-spring-starter").projectDir = file("spring/spring-starter")

// Extensions
project(":aws-xray-extension-core").projectDir = file("extensions/aws-xray-extension/core")
project(":aws-xray-extension-spring-boot").projectDir = file("extensions/aws-xray-extension/spring-boot")
project(":brave-extension-core").projectDir = file("extensions/brave-extension/core")
project(":brave-extension-spring-boot").projectDir = file("extensions/brave-extension/spring-boot")
project(":core-kotlin-dsl").projectDir = file("extensions/core-kotlin-dsl")
project(":java-dynamic-sqs-listener-ktor-core").projectDir = file("ktor/core")
project(":spring-cloud-schema-registry-extension-api").projectDir = file("extensions/spring-cloud-schema-registry-extension/spring-cloud-schema-registry-extension-api")
project(":avro-spring-cloud-schema-registry-extension").projectDir = file("extensions/spring-cloud-schema-registry-extension/avro-spring-cloud-schema-registry-extension")
project(":in-memory-spring-cloud-schema-registry").projectDir = file("extensions/spring-cloud-schema-registry-extension/in-memory-spring-cloud-schema-registry")

// Utils
project(":annotation-utils").projectDir = file("util/annotation-utils")
project(":avro-spring-cloud-schema-registry-sqs-client").projectDir = file("util/avro-spring-cloud-schema-registry-sqs-client")
project(":common-utils").projectDir = file("util/common-utils")
project(":documentation-annotations").projectDir = file("util/documentation-annotations")
project(":elasticmq-sqs-client").projectDir = file("util/elasticmq-sqs-client")
project(":expected-test-exception").projectDir = file("util/expected-test-exception")
project(":local-sqs-async-client").projectDir = file("util/local-sqs-async-client")
project(":proxy-method-interceptor").projectDir = file("util/proxy-method-interceptor")
project(":sqs-brave-tracing").projectDir = file("util/sqs-brave-tracing")

// Examples
project(":example:auto-visibility-extender-example").projectDir = file("examples/auto-visibility-extender-example")
project(":example:aws-xray-spring-example").projectDir = file("examples/aws-xray-spring-example")
project(":example:core-example").projectDir = file("examples/core-example")
project(":example:core-kotlin-example").projectDir = file("examples/core-kotlin-example")
project(":example:fifo-example").projectDir = file("examples/fifo-example")
project(":example:ktor-example").projectDir = file("examples/ktor-example")
project(":example:spring-aws-example").projectDir = file("examples/spring-aws-example")
project(":example:spring-cloud-schema-registry:consumer").projectDir = file("examples/spring-cloud-schema-registry-example/spring-cloud-schema-registry-consumer")
project(":example:spring-cloud-schema-registry:producer").projectDir = file("examples/spring-cloud-schema-registry-example/spring-cloud-schema-registry-producer")
project(":example:spring-cloud-schema-registry:producer-two").projectDir = file("examples/spring-cloud-schema-registry-example/spring-cloud-schema-registry-producer-two")
project(":example:spring-integration-test-example").projectDir = file("examples/spring-integration-test-example")
project(":example:spring-multiple-aws-account-example").projectDir = file("examples/spring-multiple-aws-account-example")
project(":example:spring-sleuth-example").projectDir = file("examples/spring-sleuth-example")
project(":example:spring-starter-example").projectDir = file("examples/spring-starter-example")
project(":example:spring-starter-minimal-example").projectDir = file("examples/spring-starter-minimal-example")
project(":example:sqs-listener-library-comparison").projectDir = file("examples/sqs-listener-library-comparison")
