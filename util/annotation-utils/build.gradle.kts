
description = "Utility methods for dealing with annotations in a library that may be using proxying tools like cglib"

dependencies {
    implementation(project(":java-dynamic-sqs-listener-api"))
    implementation(project(":common-utils"))
    compileOnly(project(":documentation-annotations"))

    testImplementation(project(":proxy-method-interceptor"))
}
