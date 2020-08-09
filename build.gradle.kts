import com.jashmore.gradle.GithubReleaseNotesTask
import com.jashmore.gradle.JacocoCoverallsPlugin
import com.jashmore.gradle.ReleasePlugin
import com.jashmore.gradle.release
import io.gitlab.arturbosch.detekt.detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    `java-library`
    checkstyle
    jacoco
    id("com.github.spotbugs")
    kotlin("jvm") apply false
    id("org.jlleitschuh.gradle.ktlint") apply false
    id("io.gitlab.arturbosch.detekt") apply false
}

allprojects {
    group = "com.jashmore"
    version = "4.2.1-SNAPSHOT"

    repositories {
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
        jcenter()
    }
}

val assertJVersion: String by project
val junitJupiterVersion: String by project
val logbackVersion: String by project
val lombokVersion: String by project
val mockitoVersion: String by project
val spotbugsVersion: String by project

subprojects {
    val isKotlinProject = project.name.contains("kotlin") || project.name.contains("ktor")
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    if (!isKotlinProject) {
        apply(plugin = "com.github.spotbugs")
    } else {
        apply(plugin = "kotlin")
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "io.gitlab.arturbosch.detekt")

        tasks.withType<KotlinCompile>().configureEach {
            kotlinOptions.jvmTarget = "1.8"
        }
    }

    dependencies {
        // Lombok
        compileOnly("org.projectlombok:lombok:$lombokVersion")
        annotationProcessor("org.projectlombok:lombok:$lombokVersion")
        testCompileOnly("org.projectlombok:lombok:$lombokVersion")
        testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

        // Testing (JUnit, Mockito, etc)
        testImplementation("org.assertj:assertj-core:$assertJVersion")
        testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
        testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
        testImplementation("org.mockito:mockito-core:$mockitoVersion")

        // Logging for tests
        testImplementation("ch.qos.logback:logback-core:$logbackVersion")
        testImplementation("ch.qos.logback:logback-classic:$logbackVersion")

        if (!isKotlinProject) {
            // SpotBugs
            spotbugs("com.github.spotbugs:spotbugs:$spotbugsVersion")
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"

        options.encoding = "UTF-8"
        options.compilerArgs.addAll(setOf("-Xlint:all", "-Werror", "-Xlint:-processing", "-Xlint:-serial"))
    }

    if (!isKotlinProject) {
        spotbugs {
            excludeFilter.set(file("${project.rootDir}/configuration/spotbugs/bugsExcludeFilter.xml"))
        }
    } else {
        detekt {
            failFast = true
            buildUponDefaultConfig = true
            config = files("${project.rootDir}/configuration/detekt/detekt-configuration.yml")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.test {
        // We don"t want integration tests to run in the "test" task and instead run in "integrationTest"
        exclude("it/com/**")

        // Only run Jacoco in the test phase, not the integration test phase
        jacoco {
            toolVersion = "0.8.5"
        }
    }

    val integrationTestTask = tasks.create<Test>("integrationTest") {
        include("it/com/**")
    }

    tasks.jacocoTestReport {
        reports {
            xml.isEnabled = true
            html.isEnabled = true
        }

        dependsOn(tasks.test)
    }

    tasks.jacocoTestCoverageVerification {
        mustRunAfter(tasks.jacocoTestReport)

        violationRules {
            rule {
                excludes = listOf(
                    "com.jashmore.sqs.examples*"
                )
                element = "PACKAGE"
                limit {
                    minimum = 0.80.toBigDecimal()
                    value = "COVEREDRATIO"
                    counter = "LINE"
                }
            }
        }
    }

    tasks.check {
        dependsOn(tasks.jacocoTestReport)
        dependsOn(tasks.jacocoTestCoverageVerification)
        dependsOn(integrationTestTask)
    }
}

apply<JacocoCoverallsPlugin>()

apply<ReleasePlugin>()
release {
    buildFile = getBuildFile()
    sonatypeUsername = System.getenv("OSS_SONATYPE_USERNAME")
    sonatypePassword = System.getenv("OSS_SONATYPE_PASSWORD")
    signingKey = System.getenv("GPG_SIGNING_KEY_ASCII_ARMORED_FORMAT")
    signingPassword = System.getenv("GPG_SIGNING_PASSWORD")
}

val milestonePropertyName = "milestoneVersion"
val generateReleaseNotesTaskName = "generateReleaseNotes"

tasks.register<GithubReleaseNotesTask>(generateReleaseNotesTaskName) {
    group = "release"
    description = "Task for generating the release notes from the issues in a GitHub milestone"

    githubUser = "JaidenAshmore"
    repositoryName = "java-dynamic-sqs-listener"
    groupings = {
        group {
            title = "Enhancements"
            filter = {
                labels.any { it.name == "enhancement" }
            }
            renderer = { issue, comments ->
                val releaseNotes = comments
                    .filter { it.body.contains("### Release Notes") }
                    .map { it.body.substringAfter("### Release Notes") }
                    .firstOrNull() ?: issue.body.substringAfter("### Release Notes")

                """### ${issue.title} [GH-${issue.number}]
                |
                |${releaseNotes.trim()}
                |
                """.trimMargin()
            }
        }

        group {
            title = "Bug Fixes"
            filter = { labels.any { it.name == "bug" } }
            renderer = { issue, _ -> "- [GH-${issue.number}]: ${issue.title}" }
        }

        group {
            title = "Documentation"
            filter = { labels.any { it.name == "documentation" } }
            renderer = { issue, _ -> "- [GH-${issue.number}]: ${issue.title}" }
        }
    }
    doFirst {
        if (!project.hasProperty(milestonePropertyName)) {
            throw RuntimeException(
                """
                    Required property $milestonePropertyName has not been set.
        
                    Usage: ./gradlew $generateReleaseNotesTaskName -P$milestonePropertyName=4.0.0
                    """.trimIndent()
            )
        }
        milestoneVersion = project.properties[milestonePropertyName] as String
    }
}

/**
 * Used to print out the current version so it can be saved as an output variable in a GitHub workflow.
 */
tasks.register("saveVersionForGitHub") {
    doLast {
        println("::set-output name=version::${project.version}")
    }
}
