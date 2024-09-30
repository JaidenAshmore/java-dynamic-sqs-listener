description = "Library for integrating the Java Dynamic Sqs Listener in a Micronaut application"

val micronautVersion: String by project

dependencies {
    implementation(platform("io.micronaut.platform:micronaut-platform:${micronautVersion}"))
    annotationProcessor(platform("io.micronaut.platform:micronaut-platform:${micronautVersion}"))

    api(project(":java-dynamic-sqs-listener-core"))
    api(project(":java-dynamic-sqs-listener-micronaut-api"))
    annotationProcessor("io.micronaut:micronaut-inject-java")
    implementation(project(":common-utils"))
    implementation(project(":annotation-utils"))
    compileOnly(project(":documentation-annotations"))
    implementation("io.micronaut:micronaut-inject")
    implementation("io.micronaut:micronaut-messaging")
    implementation("io.micronaut:micronaut-context")
    implementation("io.micronaut:micronaut-jackson-databind")

    testAnnotationProcessor(platform("io.micronaut.platform:micronaut-platform:${micronautVersion}"))
    testAnnotationProcessor("io.micronaut:micronaut-inject-java")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation(project(":elasticmq-sqs-client"))
    testImplementation(project(":expected-test-exception"))
}
