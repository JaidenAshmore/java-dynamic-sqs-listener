package com.jashmore.gradle

import org.eclipse.egit.github.core.Issue
import org.eclipse.egit.github.core.client.GitHubClient
import org.eclipse.egit.github.core.service.IssueService
import org.eclipse.egit.github.core.service.MilestoneService
import org.eclipse.egit.github.core.service.RepositoryService
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

typealias IssueRenderer = (issue: Issue) -> String

data class IssueGrouping(val title: String, val description: String?, val renderer: IssueRenderer, val filter: (issue: Issue) -> Boolean)

@DslMarker
annotation class ReleaseNotesDsl

@ReleaseNotesDsl
class GroupingDsl {
    /**
     * The required title of the grouping, e.g. Enhancements or bug fixes.
     */
    var title: String? = null

    /**
     * The optional description of the group that will be included underneath the title.
     */
    var description: String? = null

    /**
     * The renderer that will be able to render the release note informatiion for the issue in the group.
     */
    var renderer: (Issue.() -> String)? = null

    /**
     * The filter to determine if the issue should be present in this group.
     *
     * Note that each issue will only display in a single group and will prioritise the first group that it exists in.
     */
    var filter: Issue.() -> Boolean  = { false }
}

@ReleaseNotesDsl
class GroupingsDsl {
    var groups = mutableListOf<IssueGrouping>()

    /**
     * Add a new group into the groupings.
     */
    fun group(init: GroupingDsl.() -> Unit) {
        val grouping = GroupingDsl()
        grouping.init()
        groups.add(IssueGrouping(
                grouping.title ?: throw IllegalArgumentException("Expected field 'title' not set"),
                grouping.description,
                grouping.renderer ?: throw IllegalArgumentException("Expected field 'renderer' not set"),
                grouping.filter
        ))
    }
}

/**
 * Task for generating release notes from the issues in a GitHub milestone.
 */
open class GithubReleaseNotesTask : DefaultTask() {
    /**
     * The name of the milestone to obtain issues for, e.g. 4.0.0.
     */
    @get:Input
    var milestoneVersion: String? = null

    /**
     * The username of the GitHub user that owns the repository, e.g. JaidenAshmore.
     */
    @get:Input
    var githubUser: String? = null

    /**
     * The name of the repository, e.g. java-dynamic-sqs-listener.
     */
    @get:Input
    var repositoryName: String? = null

    /**
     * Defines the groups of issues and how to render them.
     *
     * For example, this can be used to group enhancements or bug fixes into separate groups and for each issue render the issue in some way.
     */
    @get:Input
    var groupings: GroupingsDsl.() -> Unit = { }

    /**
     * Optional auth token that can be used to use a personal or OAuth token for the client.
     */
    @get:Input
    var authToken: String = ""

    @TaskAction
    fun generate() {
        val milestoneVersion = this@GithubReleaseNotesTask.milestoneVersion ?: throw IllegalArgumentException("Required field milestoneVersion is not set")
        val githubUser = this@GithubReleaseNotesTask.githubUser ?: throw IllegalArgumentException("Required field githubUser is not set")
        val repositoryName = this@GithubReleaseNotesTask.repositoryName ?: throw IllegalArgumentException("Required field repositoryName is not set")

        val client = GitHubClient()
        if (authToken.isNotEmpty()) {
            client.setOAuth2Token(authToken)
        }
        val repository = RepositoryService(client).getRepository(githubUser, repositoryName)
        val milestone = MilestoneService(client).getMilestones(repository, "all")
                .first { it.title == milestoneVersion }

        val groupingsDsl = GroupingsDsl()
        groupingsDsl.groupings()

        val issues = IssueService(client).getIssues(repository, mutableMapOf(
                "milestone" to milestone.number.toString(),
                "state" to "closed"
        ))

        val issuesRendered = mutableSetOf<Issue>()

        val releaseMarkdown = groupingsDsl.groups.joinToString("\n") {
            val matchingIssues = issues
                    .filter(it.filter)
                    .filterNot(issuesRendered::contains)

            if (matchingIssues.isEmpty()) {
                return@joinToString ""
            }

            val renderedIssueInformation = matchingIssues
                    .onEach(issuesRendered::add)
                    .joinToString("\n", postfix = "\n") { issue -> it.renderer(issue) }
            """
            |## ${it.title}
            |${it.description ?: ""}
            |
            |$renderedIssueInformation
            """.trimMargin()
        }

        println("MD: \n$releaseMarkdown")
    }
}



