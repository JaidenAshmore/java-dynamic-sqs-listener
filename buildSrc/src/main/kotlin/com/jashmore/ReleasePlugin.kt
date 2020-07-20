package com.jashmore

import com.jashmore.utils.isSnapshotVersion
import io.codearte.gradle.nexus.NexusStagingExtension
import io.codearte.gradle.nexus.NexusStagingPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import java.io.File
import java.net.URI

open class ReleasePluginExtension {
    /**
     * The root build file that will be used to update the version number to a release or SNAPSHOT version.
     *
     * <p>This field is required.
     */
    var buildFile: File? = null

    /**
     * The username of the Sonatype account. This is the username for the <a href="https://issues.sonatype.org">Sonatype Jira system</a>.
     *
     * <p>If this field is null, you will be unable to release the artifact.
     */
    var sonatypeUsername: String? = null

    /**
     * The password of the Sonatype account. This is the password for the <a href="https://issues.sonatype.org">Sonatype Jira system</a>.
     *
     * <p>If this field is null, you will be unable to release the artifact.
     */
    var sonatypePassword: String? = null

    /**
     * The GPG Signing Key in an ASCII armored format.
     *
     * <p>If this field is null, you will be unable to release the artifact.
     */
    var signingKey: String? = null

    /**
     * The password for the GPG Signing Key.
     *
     * <p>If this field is null, you will be unable to release the artifact.
     */
    var signingPassword: String? = null
}

/**
 * Custom plugin for configuring the publishing and releasing of this library to Maven Central.
 */
open class ReleasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val projectVersion = project.version as String
        val extension = project.extensions.create<ReleasePluginExtension>("release")

        // Need to do it last so the extension information as well as subproject description is populated correctly
        project.afterEvaluate {
            val deploymentTag = if (isSnapshotVersion(projectVersion)) "HEAD" else "v$projectVersion"

            project.subprojects.forEach { subProject ->
                val isExamplesModule = subProject.projectDir.path.contains("examples")
                val moduleCompilesJava = subProject.plugins.hasPlugin("java")
                if (!isExamplesModule && moduleCompilesJava) {
                    subProject.pluginManager.apply(MavenPublishPlugin::class.java)
                    subProject.pluginManager.apply(SigningPlugin::class.java)

                    subProject.configure<JavaPluginExtension> {
                        // We need this in our releases
                        withSourcesJar()
                        withJavadocJar()
                    }

                    subProject.configure<PublishingExtension> {
                        publications {
                            create<MavenPublication>("mavenJava") {
                                groupId = subProject.group as String
                                artifactId = subProject.name.replace(":", "")
                                version = projectVersion

                                from(subProject.components["java"])

                                // Need to do it last so that the description is properly set in each of the project's gradle build files
                                subProject.afterEvaluate {
                                    pom {
                                        name.set(determineModuleName(subProject.name))
                                        description.set(subProject.description)
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
                                            tag.set(deploymentTag)
                                        }
                                    }
                                }
                            }
                        }

                        repositories {
                            maven {
                                url = if (project.version.toString().endsWith("SNAPSHOT")) {
                                    URI("https://oss.sonatype.org/content/repositories/snapshots")
                                } else {
                                    URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                                }

                                credentials {
                                    username = extension.sonatypeUsername
                                    password = extension.sonatypePassword
                                }
                            }
                        }
                    }

                    subProject.configure<SigningExtension> {
                        // We only want to sign if we are actually publishing to Maven Central
                        setRequired({ project.gradle.taskGraph.hasTask("publishMavenJavaPublicationToMavenRepository") })

                        useInMemoryPgpKeys(
                                extension.signingKey,
                                extension.signingPassword
                        )

                        val publishingExtension = subProject.extensions.getByName("publishing") as PublishingExtension
                        sign(publishingExtension.publications["mavenJava"])
                    }
                }
            }
        }

        project.pluginManager.apply(NexusStagingPlugin::class.java)

        project.afterEvaluate {
            project.configure<NexusStagingExtension> {
                username = extension.sonatypeUsername
                password = extension.sonatypePassword
                numberOfRetries = 20
                delayBetweenRetriesInMillis = 10000
            }
        }

        fun updateBuildVersion(buildFile: File?, from: String, to: String) {
            if (buildFile == null) {
                throw RuntimeException("Required field 'buildFile' is missing")
            }

            val previousText = buildFile.readText()
            val newBuildFileText = previousText.replaceFirst(Regex("version\\s*=\\s*\"$from\""), "version = \"$to\"")
            if (previousText == newBuildFileText) {
                throw RuntimeException("Build file content did not change")
            }
            buildFile.writeText(newBuildFileText)
        }

        project.tasks.register("prepareReleaseVersion") {
            group = "Release"
            description = "Remove the SNAPSHOT suffix from the version"

            doLast {
                val nonSnapshotVersion = projectVersion.replace("-SNAPSHOT", "")

                println("Changing version $projectVersion to non-snapshot version $nonSnapshotVersion")

                updateBuildVersion(extension.buildFile, from = projectVersion, to = nonSnapshotVersion)
            }
        }

        /**
         * Replaces the version in this build version to be the next SNAPSHOT version. Examples of this are:
         *
         * 1.0.0 -> 1.0.1-SNAPSHOT
         * 1.0.0-SNAPSHOT -> 1.0.1-SNAPSHOT
         * 1.0.0-M1 -> 1.0.0-M2-SNAPSHOT
         */
        project.tasks.register("prepareNextSnapshotVersion") {
            group = "Release"
            description = "Update the version of the application to be the next SNAPSHOT version"

            doLast {
                val nonSnapshotVersion = projectVersion.replace("-SNAPSHOT", "")
                val deliminator = if (nonSnapshotVersion.contains("-M")) "-M" else "."
                val lastNumber = nonSnapshotVersion.substringAfterLast(deliminator).toInt()
                val versionPrefix = nonSnapshotVersion.substringBeforeLast(deliminator)
                val nextSnapshotVersion = "$versionPrefix$deliminator${lastNumber + 1}-SNAPSHOT"

                println("Changing version $projectVersion to snapshot version $nextSnapshotVersion")

                updateBuildVersion(extension.buildFile, from = projectVersion, to = nextSnapshotVersion)
            }
        }
    }
}

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

/**
 * Extension for configuring this plugin.
 *
 * <p>Usage:
 *
 * <pre>
 * release {
 *     buildFile = getBuildFile()
 *     ...
 * }
 * </pre>
 */
fun Project.release(configure: ReleasePluginExtension.() -> Unit): Unit = (this as ExtensionAware).extensions.configure("release", configure)

