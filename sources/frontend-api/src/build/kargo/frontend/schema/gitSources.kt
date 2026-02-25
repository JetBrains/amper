/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package build.kargo.frontend.schema

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceableString
import java.nio.file.Path
import kotlin.String

/**
 * Represents a Git-based source dependency in module.yaml.
 *
 * Supports both short-hand syntax (github/gitlab/bitbucket) and full Git URLs.
 */
sealed class GitSource : SchemaNode() {

    @SchemaDoc("Git reference (tag, branch, or commit hash) to check out")
    abstract val version: TraceableString

    @SchemaDoc("Optional subdirectory containing the Kargo project")
    val path by nullableValue<Path>()

    @SchemaDoc("If true, build and publish artifacts without injecting them as dependencies (default: false)")
    val publishOnly by value(false)

    @SchemaDoc("Optional platforms to build (defaults to all platforms supported by the source)")
    val platforms by nullableValue<List<TraceableEnum<Platform>>>()
}
/**
 * Short-hand syntax for GitHub repositories.
 * Example: `github: user/repo`
 */
class GitHubSource : GitSource() {
    @SchemaDoc("GitHub repository in format: user/repo")
    val github by value<String>()

    override val version by value<TraceableString>()
}
/**
 * Short-hand syntax for GitLab repositories.
 * Example: `gitlab: group/project`
 */
class GitLabSource : GitSource() {
    @SchemaDoc("GitLab repository in format: group/project")
    val gitlab by value<String>()

    override val version by value<TraceableString>()
}
/**
 * Short-hand syntax for Bitbucket repositories.
 * Example: `bitbucket: user/repo`
 */
class BitbucketSource : GitSource() {
    @SchemaDoc("Bitbucket repository in format: user/repo")
    val bitbucket by value<String>()

    override val version by value<TraceableString>()
}
/**
 * Full Git URL syntax for custom/private repositories.
 * Example: `git: ssh://git@server.com/repo.git`
 */
class GitUrlSource : GitSource() {
    @SchemaDoc("Full Git repository URL (HTTPS or SSH)")
    val git by value<String>()

    override val version by value<TraceableString>()
}
