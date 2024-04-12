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
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tools.JaegerTool
import org.jetbrains.amper.tools.Tool
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.system.exitProcess

private class RootCommand : CliktCommand(name = "amper") {
    init {
        versionOption(version = AmperBuild.BuildNumber)
        subcommands(
            CleanCommand(),
            CleanSharedCachesCommand(),
            TestCommand(),
            BuildCommand(),
            NewCommand(),
            RunCommand(),
            TaskCommand(),
            TasksCommand(),
            ToolCommand(),
            PublishCommand(),
        )
    }

    val root by option(help = "Amper project root")
        .path(mustExist = true, canBeFile = false, canBeDir = true)
        .defaultLazy { Path(System.getProperty("user.dir")) }

    val debug by option(help = "Enable debug output").flag(default = false)

    val sharedCachesRoot by option(
        "--shared-caches-root",
        help = "Custom shared caches root " +
                // see org.jetbrains.amper.cli.AmperUserCacheRoot.Companion.fromCurrentUser
                when (DefaultSystemInfo.detect().family) {
                    SystemInfo.OsFamily.Windows -> "(default: %LOCALAPPDATA%/Amper)"
                    SystemInfo.OsFamily.Linux -> "(default: ~/.cache/Amper)"
                    SystemInfo.OsFamily.MacOs -> "(default: ~/Library/Caches/Amper)"
                    else -> ""
                })
        .path(canBeFile = false)

    val asyncProfiler by option(help = "Profile Amper with Async Profiler").flag(default = false)

    val buildOutputRoot by option(
        "--build-output",
        help = "Build output root. 'build' directory under project root by default"
    ).path(mustExist = false, canBeFile = false, canBeDir = true)

    override fun run() {
        // TODO think of a better place to activate it. e.g. we need it in tests too
        // TODO disabled jul bridge for now since it reports too much in debug mode
        //  and does not handle source class names from jul LogRecord
        // JulTinylogBridge.activate()

        CliEnvironmentInitializer.setup()

        val projectContext = ProjectContext.create(
            projectRoot = root,
            buildOutputRoot = buildOutputRoot?.let {
                it.createDirectories()
                AmperBuildOutputRoot(it.toAbsolutePath())
            },
            userCacheRoot = sharedCachesRoot?.let { AmperUserCacheRoot(it.toAbsolutePath()) } ,
        )

        CliEnvironmentInitializer.setupDeadLockMonitor(projectContext.buildLogsRoot)
        CliEnvironmentInitializer.setupTelemetry(projectContext.buildLogsRoot)
        CliEnvironmentInitializer.setupLogging(projectContext.buildLogsRoot, enableConsoleDebugLogging = debug)

        if (asyncProfiler) {
            AsyncProfilerMode.attachAsyncProfiler(projectContext.buildLogsRoot, projectContext.buildOutputRoot)
        }

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

private class CleanSharedCachesCommand : CliktCommand(name = "clean-shared-caches", help = "Remove shared caches") {
    val amperBackend by requireObject<AmperBackend>()

    @OptIn(ExperimentalPathApi::class)
    override fun run() {
        val root = amperBackend.context.userCacheRoot
        LoggerFactory.getLogger(javaClass).info("Deleting ${root.path}")
        root.path.deleteRecursively()
    }
}

private class TaskCommand : CliktCommand(name = "task", help = "Execute any task from task graph") {
    val name by argument(help = "task name to execute")
    val amperBackend by requireObject<AmperBackend>()
    override fun run() {
        return runBlocking { amperBackend.runTask(TaskName(name)) }
    }
}

private class RunCommand : CliktCommand(name = "run", help = "Run your application. Use -- to separate application's arguments from Amper options") {
    val platform by option(
        "-p",
        "--platform",
        help = "Run under specified platform",
        completionCandidates = CompletionCandidates.Fixed(prettyLeafPlatforms.keys),
    ).validate { value ->
        checkPlatform(value)
    }

    val buildType by option(
        "-b",
        "--build-type",
        help = "Run under specified build type",
        completionCandidates = CompletionCandidates.Fixed(BuildType.buildTypeStrings)
    ).validate { value ->
        checkBuildType(value)
    }

    private fun checkBuildType(value: String) {
        BuildType.byValue(value) ?: userReadableError("Unsupported build type '$value'.\n\nPossible values: ${BuildType.buildTypeStrings}")
    }

    val programArguments by argument(name = "program arguments").multiple()

    val module by option("-m", "--module", help = "specific module to run")
    val amperBackend by requireObject<AmperBackend>()
    override fun run() {
        val platformToRun = platform?.let { prettyLeafPlatforms.getValue(it) }
        val commonRunSettings = CommonRunSettings(programArgs = programArguments)
        val amperBackendWithRunSettings = AmperBackend(
            context = amperBackend.context.withCommonRunSettings(commonRunSettings),
        )
        val buildType = buildType?.let { BuildType.byValue(it) }?: BuildType.Debug
        amperBackendWithRunSettings.runApplication(platform = platformToRun, moduleName = module, buildType = buildType)
    }
}

private class TasksCommand : CliktCommand(name = "tasks", help = "Show tasks in the project") {
    val amperBackend by requireObject<AmperBackend>()
    override fun run() = amperBackend.showTasks()
}

private class TestCommand : CliktCommand(name = "test", help = "Run tests in the project") {
    val platform by platformOption()
    val filter by option("-f", "--filter", help = "wildcard filter to run only matching tests, the option could be repeated to run tests matching any filter")
    val module by option("-m", "--module", help = "specific module to check, the option could be repeated to check several modules")
    val amperBackend by requireObject<AmperBackend>()
    override fun run() {
        if (filter != null) {
            userReadableError("Filters are not implemented yet")
        }

        // try to execution as many tests as possible
        val amperBackendWithExecutionMode = AmperBackend(
            context = amperBackend.context.withTaskExecutionMode(TaskExecutor.Mode.GREEDY),
        )

        amperBackendWithExecutionMode.check(
            platforms = platform.ifEmpty { null }?.toSet(),
            moduleName = module,
        )
    }
}

private class PublishCommand : CliktCommand(name = "publish", help = "Publish modules to a repository") {
    val module by option("-m", "--modules", help = "specify modules to publish, delimited by ','. " +
            "By default 'publish' command will publish all possible modules").split(",")
    val amperBackend by requireObject<AmperBackend>()
    val repositoryId by argument("repository-id")
    override fun run() {
        amperBackend.publish(
            modules = module?.toSet(),
            repositoryId = repositoryId,
        )
    }
}

private class ToolCommand : CliktCommand(name = "tool", help = "Run a tool") {
    val tool by argument(name = "tool", help = "available: ${tools.joinToString(" ") { it.name }}")
    val toolArguments by argument(name = "tool arguments").multiple()
    val amperBackend by requireObject<AmperBackend>()
    override fun run() {
        val toolObj = tools.firstOrNull { it.name == tool }
            ?: userReadableError("Tool '$tool' was not found. Available tools: ${tools.joinToString(" ") { it.name }}")
        toolObj.run(toolArguments, userCacheRoot = amperBackend.context.userCacheRoot)
    }

    companion object {
        private val tools = listOf<Tool>(JaegerTool)
    }
}

private class BuildCommand : CliktCommand(name = "build", help = "Compile and link all code in the project") {
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
