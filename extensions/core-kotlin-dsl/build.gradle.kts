description = "Kotlin DSL for building the Core message listeners"

val jacksonVersion: String by project
val mockitoKotlinVersion: String by project

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api(project(":java-dynamic-sqs-listener-core"))

    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":expected-test-exception"))
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
}
