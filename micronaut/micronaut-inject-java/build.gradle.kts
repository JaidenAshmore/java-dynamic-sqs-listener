description = "Micronaut annotation processing for core annotations"

val micronautVersion: String by project

configurations.annotationProcessor {
    extendsFrom(configurations.implementation.get())
}

dependencies {
    implementation(platform("io.micronaut.platform:micronaut-platform:${micronautVersion}"))
    implementation("io.micronaut:micronaut-inject-java")
}