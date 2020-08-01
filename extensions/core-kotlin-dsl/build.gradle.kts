description = "Kotlin DSL for building the Core message listeners"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api(project(":java-dynamic-sqs-listener-core"))

    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":expected-test-exception"))
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
