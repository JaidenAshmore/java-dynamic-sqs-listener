
description = "Utility methods for dealing with annotations in a library that may be using proxying tools like cglib<"

dependencies {
    implementation(project(":java-dynamic-sqs-listener-api"))
    implementation(project(":util:common-utils"))
    implementation(project(":util:documentation-annotations"))

    testImplementation(project(":util:proxy-method-interceptor"))
}
