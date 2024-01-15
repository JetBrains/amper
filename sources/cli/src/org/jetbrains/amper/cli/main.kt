/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.file
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import java.io.File

private class RootCommand : CliktCommand(name = "amper") {
    init {
        versionOption(version = AmperBuild.BuildNumber)
        subcommands(
            CheckCommand(),
            CompileCommand(),
            NewCommand(),
            RunCommand(),
            TaskCommand(),
            TasksCommand(),
        )
    }

    val root by option(help = "Amper project root")
        .file(mustExist = true, canBeFile = false, canBeDir = true)
        .defaultLazy { File(System.getProperty("user.dir")) }

    val buildOutputRoot by option(
        "--build-output",
        help = "Build output root. 'build' directory under project root by default"
    ).file(mustExist = true, canBeFile = false, canBeDir = true)

    override fun run() {
        // TODO think of a better place to activate it. e.g. we need it in tests too
        // TODO disabled jul bridge for now since it reports too much in debug mode
        //  and does not handle source class names from jul LogRecord
        // JulTinylogBridge.activate()

        val projectRoot = root.toPath()
        val buildOutput = buildOutputRoot?.toPath() ?: projectRoot.resolve("build")
        val projectContext = ProjectContext(
            projectRoot = AmperProjectRoot(projectRoot),
            buildOutputRoot = AmperBuildOutputRoot(buildOutput),
            projectTempRoot = AmperProjectTempRoot(buildOutput.resolve("temp")),
            userCacheRoot = AmperUserCacheRoot.fromCurrentUser(),
        )
        val backend = AmperBackend(context = projectContext)
        currentContext.obj = backend
    }
}

private class NewCommand : CliktCommand(name = "new", help = "New Amper project") {
    val template by argument(help = "project template name, e.g., 'cli'")
    val amperBackend by requireObject<AmperBackend>()
    override fun run() = TODO()
}

private class TaskCommand : CliktCommand(name = "task", help = "Execute any task from task graph") {
    val name by argument(help = "task name to execute")
    val amperBackend by requireObject<AmperBackend>()
    override fun run() = amperBackend.runTask(TaskName(name))
}

private class RunCommand : CliktCommand(name = "run", help = "Initialize Amper project ") {
    val platform by option(
        help = "Run under specified platform",
        completionCandidates = CompletionCandidates.Fixed(prettyLeafPlatforms.keys),
    ).validate { value ->
        require(prettyLeafPlatforms.containsKey(value)) {
            "Unsupported platform '$value'. Possible values: $prettyLeafPlatformsString"
        }
    }

    val module by option(help = "specific module to run")
    val amperBackend by requireObject<AmperBackend>()
    override fun run() {
        val platformToRun = platform?.let { prettyLeafPlatforms.getValue(it) }
        amperBackend.run(platform = platformToRun, moduleName = module)
    }
}

private class TasksCommand : CliktCommand(name = "tasks", help = "Show tasks in the project") {
    val amperBackend by requireObject<AmperBackend>()
    override fun run() = amperBackend.showTasks()
}

private class CheckCommand : CliktCommand(name = "check", help = "Run tests in the project") {
    val platform by platformOption()
    val filter by option(help = "wildcard filter to run only matching tests")
    val amperBackend by requireObject<AmperBackend>()
    override fun run() = TODO()
}

private class CompileCommand : CliktCommand(name = "compile", help = "Compile and link all code in the project") {
    val platform by platformOption()
    val amperBackend by requireObject<AmperBackend>()
    override fun run() = amperBackend.compile(platforms = if (platform.isEmpty()) null else platform.toSet())
}

private val prettyLeafPlatforms = Platform.leafPlatforms.associateBy { it.pretty }
private val prettyLeafPlatformsString = prettyLeafPlatforms.keys.sorted().joinToString(" ")

private fun ParameterHolder.platformOption() = option(
    help = "Limit to the specified platforms",
    completionCandidates = CompletionCandidates.Fixed(prettyLeafPlatforms.keys),
).transformAll { values ->
    for (value in values) {
        require(prettyLeafPlatforms.containsKey(value)) {
            fail("Unsupported platform '$value'. Possible values: $prettyLeafPlatformsString")
        }
    }

    values.map { prettyLeafPlatforms[it]!! }
}

fun main(args: Array<String>) = RootCommand().main(args)
