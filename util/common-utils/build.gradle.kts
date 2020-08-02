
description = "Utility methods for dealing with basic functionality for this library, split out so so it can be consumed by other extensions"

val slf4jVersion: String by project

dependencies {
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    compileOnly(project(":documentation-annotations"))

    testImplementation(project(":proxy-method-interceptor"))
    testImplementation(project(":expected-test-exception"))
}
