
description = "Utility methods for dealing with basic functionality for this library, split out so so it can be consumed by other extensions"

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation(project(":util:documentation-annotations"))

    testImplementation(project(":util:proxy-method-interceptor"))
    testImplementation(project(":util:expected-test-exception"))
}
