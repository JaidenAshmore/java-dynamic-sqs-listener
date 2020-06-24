plugins {
    java
    `java-library`
    id("com.github.spotbugs") version "4.4.2"
    checkstyle
    jacoco
    id("com.github.kt3k.coveralls") version "2.10.1"
}

allprojects {
    group = "com.jashmore"
    version = "4.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.github.spotbugs")
    apply(plugin = "checkstyle")
    apply(plugin = "jacoco")
    apply(plugin = "com.github.kt3k.coveralls")

    dependencies {
        // BOMs
        implementation(platform("software.amazon.awssdk:bom:2.13.7"))
        implementation(platform("org.springframework.boot:spring-boot-dependencies:2.3.1.RELEASE"))

        // Lombok
        compileOnly("org.projectlombok:lombok:1.18.12")
        annotationProcessor("org.projectlombok:lombok:1.18.12")
        testCompileOnly("org.projectlombok:lombok:1.18.12")
        testAnnotationProcessor("org.projectlombok:lombok:1.18.12")

        // Testing (JUnit, Mockito, etc)
        testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
        testImplementation("org.mockito:mockito-junit-jupiter:3.3.3")
        testImplementation("org.mockito:mockito-core:3.3.3")
        testImplementation("org.hamcrest:hamcrest:2.2")
        testImplementation("org.assertj:assertj-core:3.16.1")

        // Logging for tests
        testImplementation("ch.qos.logback:logback-core:1.2.3")
        testImplementation("ch.qos.logback:logback-classic:1.2.3")

        // SpotBugs
        spotbugs("com.github.spotbugs:spotbugs:4.0.2")

        constraints {
            // Proxying
            implementation("cglib:cglib:3.3.0")

            // Slf4j
            implementation("org.slf4j:slf4j-api:1.7.30")

            // Brave
            implementation("io.zipkin.brave:brave:5.12.3")
            implementation("io.zipkin.brave:brave-context-slf4j:5.12.3")
            implementation("io.zipkin.brave:brave-tests:5.12.3")

            // ElasticMQ
            implementation("org.elasticmq:elasticmq-rest-sqs_2.12:0.15.6")
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(setOf("-Xlint:all", "-Werror", "-Xlint:-processing", "-Xlint:-serial"))
    }

    java {
        withSourcesJar()
        withJavadocJar()
    }

    checkstyle {
        configFile = file("${project.rootDir}/configuration/checkstyle/google_6_18_checkstyle.xml")
        maxWarnings = 0
        maxErrors = 0
    }
    tasks.withType<Checkstyle> {
        // Needed because this is generated code and I am not good enough with gradle to properly exclude these source files
        // from only the checkstyleTest task
        exclude("**com/jashmore/sqs/extensions/registry/model/*")
    }

    spotbugs {
        excludeFilter.set(file("${project.rootDir}/configuration/spotbugs/bugsExcludeFilter.xml"))
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

    tasks.withType<Test> {
        useJUnitPlatform()
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
                excludes = listOf("com.jashmore.sqs.examples*")
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

// we explicitly exclude the sub-project folders, as well as modules that have no tests and therefore will not generate jacoco reports
// if we can do this smarter, that would be nice
val excludedSubprojects = setOf("examples", "extensions", "util",
        "java-dynamic-sqs-listener-api", "java-dynamic-sqs-listener-spring", "sqs-listener-library-comparison",
        "spring-cloud-schema-registry-extension", "avro-spring-cloud-schema-registry-sqs-client",
        "documentation-annotations", "elasticmq-sqs-client", "expected-test-exception", "proxy-method-interceptor",
        "spring-cloud-schema-registry-consumer", "spring-cloud-schema-registry-producer", "spring-cloud-schema-registry-producer-two",
        "in-memory-spring-cloud-schema-registry"
)

val jacocoFullReportSubProjects = subprojects
        .filter { excludedSubprojects.none { subProjectName -> it.name.startsWith(subProjectName) } }
        .filter { !it.name.endsWith("-examples") && !it.name.endsWith("-example") }

// Inspired by: https://stackoverflow.com/a/60636581
val jacocoMerge = tasks.register<JacocoMerge>("jacocoMerge") {
    description = "Combine all of the submodule Jacoco execution data files into a single execution data file"

    dependsOn(subprojects.map { it.tasks.test })

    executionData(jacocoFullReportSubProjects
            .flatMap { it.tasks.withType<JacocoReport>() }
            .flatMap { it.executionData.files })

    println("ExecutionData: ${executionData.map { it.absolutePath }}")

    destinationFile = file("$buildDir/jacoco")
}

val jacocoRootReportTask = tasks.register<JacocoReport>("jacocoRootReport") {
    description = "Generate a single Jacoco Report from the submodules"
    dependsOn(jacocoMerge)

    sourceDirectories.from(files(jacocoFullReportSubProjects.map { it.the<SourceSetContainer>()["main"].allSource.srcDirs }))

    classDirectories.from(files(jacocoFullReportSubProjects.map { it.the<SourceSetContainer>()["main"].output }))

    executionData(jacocoMerge.get().destinationFile)

    reports {
        html.isEnabled = true
        xml.isEnabled = true
        csv.isEnabled = false
    }
}

coveralls {
    sourceDirs = jacocoFullReportSubProjects.flatMap { it.sourceSets.main.get().allSource.srcDirs.map { dir -> dir.path } }
    jacocoReportPath = "${buildDir}/reports/jacoco/jacocoRootReport/jacocoRootReport.xml"
}

tasks.coveralls {
    group = "coverage reports"
    description = "Uploads the aggregated coverage report to Coveralls"

    dependsOn(jacocoRootReportTask)

    onlyIf { env["CI"].equals("true", true) }
}
