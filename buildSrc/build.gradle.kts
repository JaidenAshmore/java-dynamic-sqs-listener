repositories {
    mavenCentral()
    jcenter()
    maven(url ="https://plugins.gradle.org/m2/")
}

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("gradle.plugin.org.kt3k.gradle.plugin:coveralls-gradle-plugin:2.10.1")
    implementation("io.codearte.gradle.nexus:gradle-nexus-staging-plugin:0.21.2")
    implementation(gradleKotlinDsl())
}


kotlinDslPluginOptions {
    experimentalWarning.set(false)
}