/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.core.IntelliJPlatformInitializer
import org.jetbrains.amper.diagnostics.DynamicLevelLoggingProvider
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.tinylog.Level
import kotlin.io.path.Path
import kotlin.system.exitProcess

private class RootCommand : CliktCommand(name = "amper") {
    init {
        versionOption(version = AmperBuild.BuildNumber)
        subcommands(
            CleanCommand(),
            CheckCommand(),
            CompileCommand(),
            NewCommand(),
            RunCommand(),
            TaskCommand(),
            TasksCommand(),
        )
    }

    val root by option(help = "Amper project root")
        .path(mustExist = true, canBeFile = false, canBeDir = true)
        .defaultLazy { Path(System.getProperty("user.dir")) }

    val debug by option(help = "Enable debug output").flag(default = false)

    val buildOutputRoot by option(
        "--build-output",
        help = "Build output root. 'build' directory under project root by default"
    ).path(mustExist = true, canBeFile = false, canBeDir = true)

    override fun run() {
        // TODO think of a better place to activate it. e.g. we need it in tests too
        // TODO disabled jul bridge for now since it reports too much in debug mode
        //  and does not handle source class names from jul LogRecord
        // JulTinylogBridge.activate()

        IntelliJPlatformInitializer.setup()

        val provider = org.tinylog.provider.ProviderRegistry.getLoggingProvider() as DynamicLevelLoggingProvider
        provider.setActiveLevel(if (debug) Level.DEBUG else Level.INFO)

        val buildOutput = buildOutputRoot ?: root.resolve("build")
        val projectContext = ProjectContext(
            projectRoot = AmperProjectRoot(root),
            buildOutputRoot = AmperBuildOutputRoot(buildOutput),
            projectTempRoot = AmperProjectTempRoot(buildOutput.resolve("temp")),
            userCacheRoot = AmperUserCacheRoot.fromCurrentUser(),
        )
        val backend = AmperBackend(context = projectContext)
        currentContext.obj = backend
    }
}

private class NewCommand : CliktCommand(name = "new", help = "New Amper project") {
    val template by argument(help = "project template name, e.g., 'cli'").optional()
    val amperBackend by requireObject<AmperBackend>()
    override fun run() = amperBackend.newProject(template = template)
}

private class CleanCommand : CliktCommand(name = "clean", help = "Remove project's build output and caches") {
    val amperBackend by requireObject<AmperBackend>()
    override fun run() = amperBackend.clean()
}

private class TaskCommand : CliktCommand(name = "task", help = "Execute any task from task graph") {
    val name by argument(help = "task name to execute")
    val amperBackend by requireObject<AmperBackend>()
    override fun run() = amperBackend.runTask(TaskName(name))
}

private class RunCommand : CliktCommand(name = "run", help = "Run your application") {
    val platform by option(
        "-p",
        "--platform",
        help = "Run under specified platform",
        completionCandidates = CompletionCandidates.Fixed(prettyLeafPlatforms.keys),
    ).validate { value ->
        checkPlatform(value)
    }

    val module by option("-m", "--module", help = "specific module to run")
    val amperBackend by requireObject<AmperBackend>()
    override fun run() {
        val platformToRun = platform?.let { prettyLeafPlatforms.getValue(it) }
        amperBackend.runApplication(platform = platformToRun, moduleName = module)
    }
}

private class TasksCommand : CliktCommand(name = "tasks", help = "Show tasks in the project") {
    val amperBackend by requireObject<AmperBackend>()
    override fun run() = amperBackend.showTasks()
}

private class CheckCommand : CliktCommand(name = "check", help = "Run tests in the project") {
    val platform by platformOption()
    val filter by option("-f", "--filter", help = "wildcard filter to run only matching tests, the option could be repeated to run tests matching any filter")
    val module by option("-m", "--module", help = "specific module to check, the option could be repeated to check several modules")
    val amperBackend by requireObject<AmperBackend>()
    override fun run() {
        if (filter != null) {
            userReadableError("Filters are not implemented yet")
        }

        amperBackend.check(
            platforms = platform.ifEmpty { null }?.toSet(),
            moduleName = module,
        )
    }
}

private class CompileCommand : CliktCommand(name = "compile", help = "Compile and link all code in the project") {
    val platform by platformOption()
    val amperBackend by requireObject<AmperBackend>()
    override fun run() = amperBackend.compile(platforms = if (platform.isEmpty()) null else platform.toSet())
}

private val prettyLeafPlatforms = Platform.leafPlatforms.associateBy { it.pretty }
private val prettyLeafPlatformsString = prettyLeafPlatforms.keys.sorted().joinToString(" ")

private fun ParameterHolder.platformOption() = option(
    "-p",
    "--platform",
    help = "Limit to the specified platform, the option could be repeated to do the action on several platforms",
    completionCandidates = CompletionCandidates.Fixed(prettyLeafPlatforms.keys),
).transformAll { values ->
    for (value in values) {
        checkPlatform(value)
    }

    values.map {
        prettyLeafPlatforms[it] ?: error("Internal error: no leaf platforms")
    }
}

private fun checkPlatform(value: String) {
    if (!prettyLeafPlatforms.containsKey(value)) {
        userReadableError("Unsupported platform '$value'.\n\nPossible values: $prettyLeafPlatformsString")
    }
}

fun main(args: Array<String>) {
    try {
        RootCommand().main(args)
    } catch (t: UserReadableError) {
        System.err.println()
        System.err.println("ERROR: ${t.message}")
        System.err.println()
        exitProcess(1)
    } catch (t: Throwable) {
        System.err.println()
        System.err.print("ERROR: ")
        t.printStackTrace()
        exitProcess(1)
    }
}
