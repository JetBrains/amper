/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tools.JaegerToolCommand
import org.jetbrains.amper.tools.JdkToolCommands
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import org.tinylog.Level
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.system.exitProcess

internal class RootCommand : CliktCommand(name = System.getProperty("amper.wrapper.process.name") ?: "amper") {
    init {
        versionOption(version = AmperBuild.BuildNumber, message = { AmperBuild.banner })
        subcommands(
            BuildCommand(),
            CleanCommand(),
            CleanSharedCachesCommand(),
            InitCommand(),
            ModulesCommand(),
            PublishCommand(),
            RunCommand(),
            TaskCommand(),
            TasksCommand(),
            TestCommand(),
            ToolCommand(),
        )
        context {
            helpFormatter = { context ->
                object : MordantHelpFormatter(context, showDefaultValues = true) {
                    override fun renderRepeatedMetavar(metavar: String): String {
                        // make it clear that arguments should be separated by '--'
                        if (metavar == "[<program arguments>]" || metavar == "[<tool arguments>]") {
                            return "-- ${metavar}..."
                        }
                        return super.renderRepeatedMetavar(metavar)
                    }
                }
            }
        }
    }

    val root by option(help = "Amper project root")
        .path(mustExist = true, canBeFile = false, canBeDir = true)
        .defaultLazy { Path(System.getProperty("user.dir")) }

    private val consoleLogLevel by option(
        "--log-level",
        help = "Set console logging level"
    ).choice(
        mapOf(
            "debug" to Level.DEBUG,
            "info" to Level.INFO,
            "warn" to Level.WARN,
            "error" to Level.ERROR,
            "off" to Level.OFF,
        ), ignoreCase = true
    ).default(Level.INFO)

    private val sharedCachesRoot by option(
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

    private val asyncProfiler by option(help = "Profile Amper with Async Profiler").flag(default = false)

    val buildOutputRoot by option(
        "--build-output",
        help = "Build output root. 'build' directory under project root by default"
    ).path(mustExist = false, canBeFile = false, canBeDir = true)

    override fun run() {
        currentContext.obj = CommonOptions(
            root = root,
            consoleLogLevel = consoleLogLevel,
            asyncProfiler = asyncProfiler,
            sharedCachesRoot = sharedCachesRoot,
            buildOutputRoot = buildOutputRoot,
        )
    }

    data class CommonOptions(
        val root: Path,
        val consoleLogLevel: Level,
        val asyncProfiler: Boolean,
        val sharedCachesRoot: Path?,
        val buildOutputRoot: Path?,
    )
}

private suspend fun cancelAndWaitForScope(scope: CoroutineScope) {
    val normalTerminationMessage = "terminating scope normally"

    try {
        val job = scope.coroutineContext.job
        job.cancel(normalTerminationMessage)
        job.join()
    } catch (t: Throwable) {
        if (t.message != normalTerminationMessage) {
            throw t
        }
    }
}

private val backendInitialized = atomic<Throwable?>(null)

internal fun withBackend(
    commonOptions: RootCommand.CommonOptions,
    currentCommand: String,
    commonRunSettings: CommonRunSettings = CommonRunSettings(),
    taskExecutionMode: TaskExecutor.Mode = TaskExecutor.Mode.FAIL_FAST,
    block: suspend (AmperBackend) -> Unit,
) {
    val initializedException = backendInitialized.getAndSet(Throwable())
    if (initializedException != null) {
        throw IllegalStateException("withBackend was already called, see nested exception", initializedException)
    }

    // TODO think of a better place to activate it. e.g. we need it in tests too
    // TODO disabled jul bridge for now since it reports too much in debug mode
    //  and does not handle source class names from jul LogRecord
    // JulTinylogBridge.activate()

    CliEnvironmentInitializer.setup()

    val projectContext = ProjectContext.create(
        projectRoot = commonOptions.root,
        buildOutputRoot = commonOptions.buildOutputRoot?.let {
            it.createDirectories()
            AmperBuildOutputRoot(it.toAbsolutePath())
        },
        userCacheRoot = commonOptions.sharedCachesRoot?.let {
            it.createDirectories()
            AmperUserCacheRoot(it.toAbsolutePath())
        },
        currentTopLevelCommand = currentCommand,
        commonRunSettings = commonRunSettings,
        taskExecutionMode = taskExecutionMode,
    )

    CliEnvironmentInitializer.setupDeadLockMonitor(projectContext.buildLogsRoot, projectContext.terminal)
    CliEnvironmentInitializer.setupTelemetry(projectContext.buildLogsRoot)
    CliEnvironmentInitializer.setupLogging(
        logsRoot = projectContext.buildLogsRoot,
        consoleLogLevel = commonOptions.consoleLogLevel,
        terminal = projectContext.terminal,
    )

    // TODO output version, os and some env to log file only
    projectContext.terminal.println(AmperBuild.banner)
    projectContext.terminal.println("Logs are in ${projectContext.buildLogsRoot.path}")
    projectContext.terminal.println()

    if (commonOptions.asyncProfiler) {
        AsyncProfilerMode.attachAsyncProfiler(projectContext.buildLogsRoot, projectContext.buildOutputRoot)
    }

    runBlocking(Dispatchers.Default) {
        // do not fail on child cancellation
        supervisorScope {
            val backgroundScope = CoroutineScope(coroutineContext + Job())
            val backend = AmperBackend(context = projectContext, backgroundScope = backgroundScope)

            block(backend)

            cancelAndWaitForScope(backgroundScope)
        }
    }
}

private class InitCommand : CliktCommand(name = "init", help = "Initialize Amper project") {
    val template by argument(help = "project template name substring, e.g., 'jvm-cli'").optional()
    val commonOptions by requireObject<RootCommand.CommonOptions>()
    override fun run() {
        withBackend(commonOptions, commandName) { backend ->
            backend.initProject(template = template)
        }
    }
}

private class CleanCommand : CliktCommand(name = "clean", help = "Remove project's build output and caches") {
    val commonOptions by requireObject<RootCommand.CommonOptions>()
    override fun run() = withBackend(commonOptions, commandName) { backend ->
        backend.clean()
    }
}

private class CleanSharedCachesCommand : CliktCommand(name = "clean-shared-caches", help = "Remove shared caches") {
    val commonOptions by requireObject<RootCommand.CommonOptions>()

    @OptIn(ExperimentalPathApi::class)
    override fun run() {
        withBackend(commonOptions, commandName) { backend ->
            val root = backend.context.userCacheRoot
            LoggerFactory.getLogger(javaClass).info("Deleting ${root.path}")
            root.path.deleteRecursively()
        }
    }
}

private class TaskCommand : CliktCommand(name = "task", help = "Execute any task from task graph") {
    val name by argument(help = "task name to execute")
    val commonOptions by requireObject<RootCommand.CommonOptions>()
    override fun run() {
        return withBackend(commonOptions, commandName) { backend ->
            backend.runTask(TaskName(name))
        }
    }
}

private class RunCommand : CliktCommand(
    name = "run",
    help = "Run your application",
    epilog = "Use -- to separate application's arguments from Amper options"
) {
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
        help = "Run under specified build type (${BuildType.buildTypeStrings.sorted().joinToString(", ")})",
        completionCandidates = CompletionCandidates.Fixed(BuildType.buildTypeStrings),
    ).default(BuildType.Debug.value).validate { value -> checkBuildType(value) }

    private fun checkBuildType(value: String) {
        BuildType.byValue(value) ?: userReadableError("Unsupported build type '$value'.\n\nPossible values: ${BuildType.buildTypeStrings}")
    }

    val programArguments by argument(name = "program arguments").multiple()

    val module by option("-m", "--module", help = "Specific module to run (run 'modules' command to get modules list)")
    val commonOptions by requireObject<RootCommand.CommonOptions>()
    override fun run() {
        val platformToRun = platform?.let { prettyLeafPlatforms.getValue(it) }
        withBackend(
            commonOptions,
            commandName,
            commonRunSettings = CommonRunSettings(programArgs = programArguments),
        ) { backend ->
            val buildType = buildType.let { BuildType.byValue(it) }
            backend.runApplication(platform = platformToRun, moduleName = module, buildType = buildType)
        }
    }
}

private class TasksCommand : CliktCommand(name = "tasks", help = "Show tasks and their dependencies in the project") {
    val commonOptions by requireObject<RootCommand.CommonOptions>()
    override fun run() = withBackend(commonOptions, commandName) { backend ->
        backend.showTasks()
    }
}

private class ModulesCommand : CliktCommand(name = "modules", help = "Show modules in the project") {
    val commonOptions by requireObject<RootCommand.CommonOptions>()
    override fun run() = withBackend(commonOptions, commandName) { backend ->
        backend.showModules()
    }
}

private class TestCommand : CliktCommand(name = "test", help = "Run tests in the project") {
    val platform by platformOption()
    val filter by option("-f", "--filter", help = "wildcard filter to run only matching tests, the option could be repeated to run tests matching any filter")
    val module by option("-m", "--module", help = "specific module to check, the option could be repeated to check several modules")
    val commonOptions by requireObject<RootCommand.CommonOptions>()
    override fun run() {
        if (filter != null) {
            userReadableError("Filters are not implemented yet")
        }

        // try to execution as many tests as possible
        withBackend(
            commonOptions,
            commandName,
            taskExecutionMode = TaskExecutor.Mode.GREEDY,
        ) { backend ->
            backend.test(
                platforms = platform.ifEmpty { null }?.toSet(),
                moduleName = module,
            )
        }
    }
}

private class PublishCommand : CliktCommand(name = "publish", help = "Publish modules to a repository") {
    val module by option("-m", "--modules", help = "specify modules to publish, delimited by ','. " +
            "By default 'publish' command will publish all possible modules").split(",")
    val commonOptions by requireObject<RootCommand.CommonOptions>()
    val repositoryId by argument("repository-id")
    override fun run() {
        withBackend(commonOptions, commandName) { backend ->
            backend.publish(
                modules = module?.toSet(),
                repositoryId = repositoryId,
            )
        }
    }
}

private class ToolCommand : CliktCommand(name = "tool", help = "Run a tool") {
    init {
        subcommands(
            JaegerToolCommand(),
            JdkToolCommands(),
        )
    }

    override fun run() = Unit
}

private class BuildCommand : CliktCommand(name = "build", help = "Compile and link all code in the project") {
    val platform by platformOption()
    val commonOptions by requireObject<RootCommand.CommonOptions>()
    override fun run() = withBackend(commonOptions, commandName) { backend ->
        backend.compile(platforms = if (platform.isEmpty()) null else platform.toSet())
    }
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
    } catch (t: Throwable) {
        System.err.println()
        System.err.println("ERROR: ${t.message}")

        when {
            t is UserReadableError -> System.err.println()
            t is TaskExecutor.TaskExecutionFailed && t.cause is UserReadableError -> System.err.println()
            else -> t.printStackTrace()
        }

        exitProcess(1)
    }
}
