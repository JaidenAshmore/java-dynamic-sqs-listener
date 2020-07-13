plugins {
    java
    `java-library`
    id("com.github.spotbugs") version "4.4.2"
    checkstyle
    jacoco
    id("com.github.kt3k.coveralls") version "2.10.1"
    `maven-publish`
    signing
    id("io.codearte.nexus-staging") version "0.21.2"
}

allprojects {
    group = "com.jashmore"
    version = "4.0.0-M8-SNAPSHOT"

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
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    dependencies {
        // AWS
        implementation(platform("software.amazon.awssdk:bom:2.13.7"))

        // Spring Boot
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
        testImplementation("ch.qos.logback:logback-core")
        testImplementation("ch.qos.logback:logback-classic")

        // SpotBugs
        spotbugs("com.github.spotbugs:spotbugs:4.0.2")

        constraints {
            // Jackson
            implementation("com.fasterxml.jackson.core:jackson-databind:2.11.1")

            // Avro/Spring Cloud Schema Registry
            implementation("org.apache.avro:avro:1.9.2")
            implementation("org.springframework.cloud:spring-cloud-schema-registry-client:1.0.3.RELEASE")

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

    java {
        withSourcesJar()
        withJavadocJar()
    }

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
                        "com.jashmore.sqs.examples*",
                        "com.jashmore.sqs.extensions.xray.client"
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

    // We don't want to ever publish example projects as these are standalone Jars/Spring Boot Apps
    if (!project.projectDir.path.contains("examples")) {
        publishing {
            publications {
                create<MavenPublication>("mavenJava") {
                    groupId = project.group as String
                    artifactId = project.name.replace(":", "")
                    version = project.version as String

                    if (plugins.hasPlugin("java")) {
                        from(components["java"])

                        // Need to do it last so that the description is properly set in each of the project's gradle build files
                        afterEvaluate {
                            pom {
                                name.set(determineModuleName(project.name))
                                description.set(project.description)
                                url.set("http://github.com/jaidenashmore/java-dynamic-sqs-listener")

                                licenses {
                                    license {
                                        name.set("MIT License")
                                        url.set("http://www.opensource.org/licenses/mit-license.php")
                                    }
                                }
                                developers {
                                    developer {
                                        id.set("jaidenashmore")
                                        name.set("Jaiden Ashmore")
                                        email.set("jaidenkyleashmore@gmail.com")
                                        organization {
                                            name.set("jaidenashmore")
                                            url.set("https://github.com/jaidenashmore")
                                        }
                                    }
                                }
                                scm {
                                    connection.set("scm:git:ssh://git@github.com/jaidenashmore/java-dynamic-sqs-listener.git")
                                    developerConnection.set("scm:git:ssh://git@github.com/jaidenashmore/java-dynamic-sqs-listener.git")
                                    url.set("http://github.com/jaidenashmore/java-dynamic-sqs-listener")
                                    tag.set("HEAD")
                                }
                            }
                        }
                    }
                }
            }

            repositories {
                maven {
                    val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                    val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots")
                    url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

                    credentials {
                        username = sonatypeUsername
                        password = sonatypePassword
                    }
                }
            }
        }

        signing {
            // We only want to sign if we are actually publishing to Maven Central
            setRequired({ gradle.taskGraph.hasTask("publishMavenJavaPublicationToMavenRepository") })

            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publishing.publications["mavenJava"])
        }
    }
}

val sonatypeUsername: String? = System.getenv("OSS_SONATYPE_USERNAME")
val sonatypePassword: String? = System.getenv("OSS_SONATYPE_PASSWORD")
val signingPassword: String? = System.getenv("GPG_SIGNING_PASSWORD")
val signingKey: String? = System.getenv("GPG_SIGNING_KEY_ASCII_ARMORED_FORMAT")

/**
 * Converts a project name like ':java-dynamic-sqs-listener-api' to be 'Java Dynamic Sqs Listener Api'.
 */
fun determineModuleName(projectName: String): String {
    val updatedProjectNameBuilder = StringBuilder(projectName.replace(":", ""))
    updatedProjectNameBuilder.setCharAt(0, updatedProjectNameBuilder[0].toUpperCase())
    projectName.forEachIndexed { i, c ->
        if (c == '-') {
            updatedProjectNameBuilder.setCharAt(i, ' ')
            if (i + 1 < projectName.length) {
                updatedProjectNameBuilder.setCharAt(i + 1, projectName[i + 1].toUpperCase())
            }
        }
    }

    return updatedProjectNameBuilder.toString()
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

apply(plugin = "io.codearte.nexus-staging")

nexusStaging {
    username = sonatypeUsername
    password = sonatypePassword
    numberOfRetries = 20
    delayBetweenRetriesInMillis = 10000
}

/**
 * Replaces the version in this build version to the version without the -SNAPSHOT suffix. This will be used before releasing the library.
 */
tasks.register("prepareReleaseVersion") {
    doLast {
        val currentVersion = project.version as String
        val nonSnapshotVersion = currentVersion.replace("-SNAPSHOT", "")
        println("Changing version $currentVersion to non-snapshot version $nonSnapshotVersion")

        val newBuildFileText = buildFile.readText().replaceFirst("version = \"$currentVersion\"", "version = \"$nonSnapshotVersion\"")
        buildFile.writeText(newBuildFileText)
    }
}

/**
 * Replaces the version in this build version to be the next SNAPSHOT version. Examples of this are:
 *
 * 1.0.0 -> 1.0.1-SNAPSHOT
 * 1.0.0-SNAPSHOT -> 1.0.1-SNAPSHOT
 * 1.0.0-M1 -> 1.0.0-M2-SNAPSHOT
 */
tasks.register("prepareNextSnapshotVersion") {
    doLast {
        val currentVersion = (project.version as String)
        val nonSnapshotVersion = currentVersion.replace("-SNAPSHOT", "")
        val deliminator = if (nonSnapshotVersion.contains("-M")) "-M" else "."
        val lastNumber = nonSnapshotVersion.substringAfterLast(deliminator).toInt()
        val versionPrefix = nonSnapshotVersion.substringBeforeLast(deliminator)
        val nextSnapshotVersion = "$versionPrefix$deliminator${lastNumber + 1}-SNAPSHOT"

        println("Changing version $currentVersion to snapshot version $nextSnapshotVersion")

        val newBuildFileText = buildFile.readText().replaceFirst("version = \"$currentVersion\"", "version = \"$nextSnapshotVersion\"")
        buildFile.writeText(newBuildFileText)
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