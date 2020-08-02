
description = "Utility methods for testing a method being proxied via cglib"

val cglibVersion: String by project

dependencies {
    api("cglib:cglib:$cglibVersion")
}
