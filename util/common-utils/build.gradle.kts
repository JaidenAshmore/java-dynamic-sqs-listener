
description = "Utility methods for dealing with basic functionality for this library, split out so so it can be consumed by other extensions"

dependencies {
    implementation("org.slf4j:slf4j-api")
    compileOnly(project(":documentation-annotations"))

    testImplementation(project(":proxy-method-interceptor"))
    testImplementation(project(":expected-test-exception"))
}
