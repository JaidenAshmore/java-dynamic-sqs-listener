package com.jashmore.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.tasks.JacocoMerge
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.kt3k.gradle.plugin.CoverallsPlugin
import org.kt3k.gradle.plugin.CoverallsPluginExtension
import org.kt3k.gradle.plugin.coveralls.CoverallsTask
import java.io.File

/**
 * Plugin used to encapsulate all of the logic to send the coverage report to coveralls.
 *
 * <p>Note that usage of these tasks should be completed after a successful build of the application as the task needs to know which modules have
 * actually outputted a Jacoco report.
 *
 * <p>Usage:
 *
 * <pre>
 *     gradle build # Runs each project which will generate a jacoco report
 *     gradle jacocoRootReport # If you want to generate the root report locally
 *     gradle coveralls # In the CI build to publish the coveralls report, this will run `jacocoRootReport` for you
 * </pre>
 */
open class JacocoCoverallsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(JacocoPlugin::class.java)
        project.pluginManager.apply(CoverallsPlugin::class.java)

        val jacocoTasks = project.subprojects
                .flatMap { it.tasks.withType<JacocoReport>() }
                .filter { report -> report.executionData.files.any { it.exists() } }

        val jacocoMergeTask = project.tasks.register<JacocoMerge>("jacocoMerge") {
            description = "Combine all of the submodule Jacoco execution data files into a single execution data file"

            val subProjectsWithJacocoReports = jacocoTasks
                    .flatMap { it.executionData.files }
                    .filter { f -> f.exists() }

            executionData(subProjectsWithJacocoReports)

            destinationFile = File("${project.buildDir}/jacoco")

            doFirst {
                println("Found the following Jacoco Reports: ${subProjectsWithJacocoReports.joinToString("\n") { it.path }}")
            }
        }

        val jacocoRootReportTask = project.tasks.register<JacocoReport>("jacocoRootReport") {
            group = "coverage reports"
            description = "Generate a single Jacoco Report from the submodules"
            dependsOn(jacocoMergeTask)

            sourceDirectories.from(jacocoTasks.flatMap { it.sourceDirectories })
            classDirectories.from(jacocoTasks.flatMap { it.classDirectories })

            executionData(jacocoMergeTask.get().destinationFile)

            reports {
                html.isEnabled = true
                xml.isEnabled = true
                csv.isEnabled = false
            }
        }

        project.tasks.withType(CoverallsTask::class.java) {
            group = "coverage reports"
            description = "Uploads the aggregated coverage report to Coveralls"

            dependsOn(jacocoRootReportTask)

            onlyIf { env["CI"].equals("true", true) }
        }

        project.configure<CoverallsPluginExtension> {
            sourceDirs = jacocoRootReportTask.get().sourceDirectories.map { it.path }
            jacocoReportPath = "${project.buildDir}/reports/jacoco/jacocoRootReport/jacocoRootReport.xml"
        }
    }
}