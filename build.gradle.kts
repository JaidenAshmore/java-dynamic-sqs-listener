import com.jashmore.gradle.JacocoCoverallsPlugin
import com.jashmore.gradle.ReleasePlugin
import com.jashmore.gradle.release

plugins {
    java
    `java-library`
    checkstyle
    jacoco
    kotlin("jvm") version "1.9.0"
    id("com.github.spotbugs")
    id("com.jashmore.gradle.github.release")
    id("org.jlleitschuh.gradle.ktlint") apply false
    id("org.unbroken-dome.test-sets") version "4.0.0"
}

allprojects {
    group = "com.jashmore"
    version = "7.0.1-SNAPSHOT"

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
    val isExampleProject = project.name.contains("example")
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "org.unbroken-dome.test-sets")
    if (!isKotlinProject) {
        if (!isExampleProject) {
            apply(plugin = "com.github.spotbugs")
        }
    } else {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
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

        if (!isKotlinProject && !isExampleProject) {
            // SpotBugs
            spotbugs("com.github.spotbugs:spotbugs:$spotbugsVersion")
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"

        options.encoding = "UTF-8"
        options.compilerArgs.addAll(setOf("-Xlint:all", "-Werror", "-Xlint:-processing", "-Xlint:-serial"))
    }

    if (!isKotlinProject) {
        if (!isExampleProject) {
            spotbugs {
                excludeFilter.set(file("${project.rootDir}/configuration/spotbugs/bugsExcludeFilter.xml"))
            }
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()

        // Only run Jacoco in the test phase, not the integration test phase
        jacoco {
            toolVersion = "0.8.10"
        }
    }

    testSets {
        val integrationTest by creating {
        }
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
                    "com.jashmore.sqs.examples*",
                    // these classes are better handled by integration tests
                    "com.jashmore.sqs.container.fifo*" ,
                    "com.jashmore.sqs.container.batching*",
                    "com.jashmore.sqs.container.prefetching*",
                    "com.jashmore.sqs.micronaut.configuration*",
                    "com.jashmore.sqs.micronaut.container*",
                    "com.jashmore.sqs.micronaut.placeholder*"
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
        dependsOn(tasks.getByName("integrationTest"))
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

gitHubRelease {
    gitHubUser = "JaidenAshmore"
    repositoryName = "java-dynamic-sqs-listener"
    milestoneVersion = (project.version as String).replace("-SNAPSHOT", "")
    groupings = {
        group {
            heading = "## Enhancements"
            filter = { issue ->
                issue.labels.any { it.name == "enhancement" }
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
            heading = "## Bug Fixes"
            filter = { issue ->
                issue.labels.any { it.name == "bug" }
            }
            renderer = { issue, _ -> "- [GH-${issue.number}]: ${issue.title}" }
        }

        group {
            heading = "## Documentation"
            filter = { issue ->
                issue.labels.any { it.name == "documentation" }
            }
            renderer = { issue, _ -> "- [GH-${issue.number}]: ${issue.title}" }
        }
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
