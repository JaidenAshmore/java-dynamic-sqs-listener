import com.jashmore.gradle.JacocoCoverallsPlugin
import com.jashmore.gradle.ReleasePlugin
import com.jashmore.gradle.release

plugins {
    java
    `java-library`
    id("com.github.spotbugs") version "4.4.5"
    checkstyle
    jacoco
}

allprojects {
    group = "com.jashmore"
    version = "4.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    val isKotlinProject = project.name.contains("kotlin") || project.name.contains("ktor")
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    if (!isKotlinProject) {
        apply(plugin = "com.github.spotbugs")
        apply(plugin = "checkstyle")
    }

    dependencies {
        // AWS
        implementation(platform("software.amazon.awssdk:bom:2.13.66"))
        api(platform("software.amazon.awssdk:bom:2.13.58"))

        // Spring Boot
        api(platform("org.springframework.boot:spring-boot-dependencies:2.3.2.RELEASE"))

        // Lombok
        compileOnly("org.projectlombok:lombok:1.18.12")
        annotationProcessor("org.projectlombok:lombok:1.18.12")
        testCompileOnly("org.projectlombok:lombok:1.18.12")
        testAnnotationProcessor("org.projectlombok:lombok:1.18.12")

        // Testing (JUnit, Mockito, etc)
        testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
        testImplementation("org.mockito:mockito-junit-jupiter:3.4.6")
        testImplementation("org.mockito:mockito-core:3.4.6")
        testImplementation("org.hamcrest:hamcrest:2.2")
        testImplementation("org.assertj:assertj-core:3.16.1")

        // Logging for tests
        testImplementation("ch.qos.logback:logback-core")
        testImplementation("ch.qos.logback:logback-classic")

        if (!isKotlinProject) {
            // SpotBugs
            spotbugs("com.github.spotbugs:spotbugs:4.0.6")
        }

        constraints {
            // Jackson
            implementation("com.fasterxml.jackson.core:jackson-databind:2.11.1")

            // Avro/Spring Cloud Schema Registry
            implementation("org.apache.avro:avro:1.10.0")
            implementation("org.springframework.cloud:spring-cloud-schema-registry-client:1.0.7.RELEASE")

            // Proxying
            implementation("cglib:cglib:3.3.0")

            // Slf4j
            implementation("org.slf4j:slf4j-api:1.7.30")

            // Brave
            implementation("io.zipkin.brave:brave:5.12.3")
            implementation("io.zipkin.brave:brave-context-slf4j:5.12.3")
            implementation("io.zipkin.brave:brave-tests:5.12.4")

            // ElasticMQ
            implementation("org.elasticmq:elasticmq-rest-sqs_2.12:0.15.6")

            // AWS Xray
            implementation("com.amazonaws:aws-xray-recorder-sdk-core:2.6.1")
            implementation("com.amazonaws:aws-xray-recorder-sdk-spring:2.6.1")
            implementation("com.amazonaws:aws-xray-recorder-sdk-aws-sdk-v2-instrumentor:2.6.1")

            // Logback (for tests)
            implementation("ch.qos.logback:logback-core:1.2.3")
            implementation("ch.qos.logback:logback-classic:1.2.3")
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"

        options.encoding = "UTF-8"
        options.compilerArgs.addAll(setOf("-Xlint:all", "-Werror", "-Xlint:-processing", "-Xlint:-serial"))
    }

    if (!isKotlinProject) {
        tasks.withType<Checkstyle> {
            // Needed because this is generated code and I am not good enough with gradle to properly exclude these source files
            // from only the checkstyleTest task
            exclude("**com/jashmore/sqs/extensions/registry/model/*")
        }

        checkstyle {
            configFile = file("${project.rootDir}/configuration/checkstyle/google_6_18_checkstyle.xml")
            maxWarnings = 0
            maxErrors = 0
        }

        spotbugs {
            excludeFilter.set(file("${project.rootDir}/configuration/spotbugs/bugsExcludeFilter.xml"))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.test {
        // We don't want integration tests to run in the "test" task and instead run in "integrationTest"
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

/**
 * Used to print out the current version so it can be saved as an output variable in a GitHub workflow.
 */
tasks.register("saveVersionForGitHub") {
    doLast {
        println("::set-output name=version::${project.version}")
    }
}
