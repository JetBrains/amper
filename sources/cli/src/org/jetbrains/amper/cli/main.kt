/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.info
import com.github.ajalt.mordant.widgets.Text
import com.intellij.util.namedChildScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.get
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.use
import org.jetbrains.amper.core.useWithScope
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.SchemaBasedModelImport
import org.jetbrains.amper.frontend.valueTracking.TracesPresentation
import org.jetbrains.amper.frontend.valueTracking.compositeValueTracesInfo
import org.jetbrains.amper.generator.ProjectGenerator
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tools.JaegerToolCommand
import org.jetbrains.amper.tools.JdkToolCommands
import org.jetbrains.amper.tools.KeystoreToolCommand
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import org.tinylog.Level
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.system.exitProcess

internal class RootCommand : SuspendingCliktCommand(name = System.getProperty("amper.wrapper.process.name") ?: "amper") {
    init {
        versionOption(version = AmperBuild.mavenVersion, message = { AmperBuild.banner })
        subcommands(
            BuildCommand(),
            CleanCommand(),
            CleanSharedCachesCommand(),
            InitCommand(),
            ModulesCommand(),
            PublishCommand(),
            RunCommand(),
            SettingsCommand(),
            TaskCommand(),
            TasksCommand(),
            TestCommand(),
            ToolCommand()
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
        help = "Path to the cache directory shared between all Amper projects",
    )
        .path(canBeFile = false)
        .convert { AmperUserCacheRoot(it.toAbsolutePath()) }
        // It's ok to use a non-lazy default here because most of the time we'll use the default value anyway.
        // This also allows to have the default value in the help, we avoids duplicating the location
        .default(AmperUserCacheRoot.fromCurrentUser())

    private val asyncProfiler by option(help = "Profile Amper with Async Profiler").flag(default = false)

    val buildOutputRoot by option(
        "--build-output",
        help = "Build output root. 'build' directory under project root by default"
    ).path(mustExist = false, canBeFile = false, canBeDir = true)

    override suspend fun run() {
        val terminal = Terminal()

        currentContext.obj = CommonOptions(
            explicitRoot = root,
            consoleLogLevel = consoleLogLevel,
            asyncProfiler = asyncProfiler,
            sharedCachesRoot = sharedCachesRoot,
            buildOutputRoot = buildOutputRoot,
            terminal = terminal,
        )

        CliEnvironmentInitializer.setupConsoleLogging(
            consoleLogLevel = consoleLogLevel,
            terminal = terminal,
        )
    }

    data class CommonOptions(
        /**
         * The explicit project root provided by the user, or null if the root should be discovered.
         */
        val explicitRoot: Path?,
        val consoleLogLevel: Level,
        val asyncProfiler: Boolean,
        val sharedCachesRoot: AmperUserCacheRoot,
        val buildOutputRoot: Path?,
        val terminal: Terminal,
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

private val backendInitialized = AtomicReference<Throwable>(null)

internal suspend fun withBackend(
    commonOptions: RootCommand.CommonOptions,
    currentCommand: String,
    commonRunSettings: CommonRunSettings = CommonRunSettings(),
    taskExecutionMode: TaskExecutor.Mode = TaskExecutor.Mode.FAIL_FAST,
    setupEnvironment: Boolean = true,
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

    spanBuilder("CLI Setup: install coroutines debug probes").use {
        CliEnvironmentInitializer.setup()
    }

    withContext(Dispatchers.Default) {
        @Suppress("UnstableApiUsage")
        val backgroundScope = namedChildScope("project background scope", supervisor = true)
        commonOptions.terminal.println(AmperBuild.banner)

        val cliContext = spanBuilder("CLI Setup: create CliContext").useWithScope {
            CliContext.create(
                explicitProjectRoot = commonOptions.explicitRoot?.toAbsolutePath(),
                buildOutputRoot = commonOptions.buildOutputRoot?.let {
                    it.createDirectories()
                    AmperBuildOutputRoot(it.toAbsolutePath())
                },
                userCacheRoot = commonOptions.sharedCachesRoot,
                currentTopLevelCommand = currentCommand,
                commonRunSettings = commonRunSettings,
                taskExecutionMode = taskExecutionMode,
                backgroundScope = backgroundScope,
                terminal = commonOptions.terminal,
            )
        }

        TelemetryEnvironment.setLogsRootDirectory(cliContext.buildLogsRoot)

        if (setupEnvironment) {
            spanBuilder("CLI Setup: setup logging and monitoring").useWithScope {
                CliEnvironmentInitializer.setupDeadLockMonitor(cliContext.buildLogsRoot, cliContext.terminal)
                CliEnvironmentInitializer.setupFileLogging(cliContext.buildLogsRoot)

                // TODO output version, os and some env to log file only
                val printableLogsPath = cliContext.buildLogsRoot.path.toString().replaceWhitespaces()
                cliContext.terminal.println("Logs are in file://$printableLogsPath")
                cliContext.terminal.println()

                if (commonOptions.asyncProfiler) {
                    AsyncProfilerMode.attachAsyncProfiler(cliContext.buildLogsRoot, cliContext.buildOutputRoot)
                }
            }
        }

        spanBuilder("Execute backend").useWithScope {
            val backend = AmperBackend(context = cliContext)
            block(backend)
            cancelAndWaitForScope(backgroundScope)
        }
    }
}

private fun String.replaceWhitespaces() = replace(" ", "%20")

private class InitCommand : SuspendingCliktCommand(name = "init") {
    val template by argument(help = "project template name substring, e.g., 'jvm-cli'").optional()
    val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "Initialize a new Amper project based on a template"

    override suspend fun run() {
        val directory = commonOptions.explicitRoot ?: Path(System.getProperty("user.dir"))
        ProjectGenerator(terminal = Terminal()).initProject(template, directory)
    }
}

private class CleanCommand : SuspendingCliktCommand(name = "clean") {
    val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "Remove the project's build output and caches"

    override suspend fun run() = withBackend(commonOptions, commandName, setupEnvironment = false) { backend ->
        val rootsToClean = listOf(backend.context.buildOutputRoot.path, backend.context.projectTempRoot.path)
        for (path in rootsToClean) {
            if (path.exists()) {
                @Suppress("ReplacePrintlnWithLogging")
                println("Deleting $path")

                path.deleteRecursively()
            }
        }
    }
}

private class CleanSharedCachesCommand : SuspendingCliktCommand(name = "clean-shared-caches") {
    val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "Remove the Amper caches that are shared between projects"

    override suspend fun run() {
        withBackend(commonOptions, commandName) { backend ->
            val root = backend.context.userCacheRoot
            LoggerFactory.getLogger(javaClass).info("Deleting ${root.path}")
            root.path.deleteRecursively()
        }
    }
}

private class TaskCommand : SuspendingCliktCommand(name = "task") {
    val name by argument(help = "task name to execute")
    val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "Execute any task from the task graph"

    override suspend fun run() {
        return withBackend(commonOptions, commandName) { backend ->
            backend.runTask(TaskName(name))
        }
    }
}

private class RunCommand : SuspendingCliktCommand(name = "run") {
    val platform by option(
        "-p",
        "--platform",
        help = "Run under specified platform",
        completionCandidates = CompletionCandidates.Fixed(prettyLeafPlatforms.keys),
    ).validate { value ->
        checkAndGetPlatform(value)
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

    override fun help(context: Context): String = "Run your application"

    override fun helpEpilog(context: Context): String = "Use -- to separate the application's arguments from Amper options"

    override suspend fun run() {
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

private class TasksCommand : SuspendingCliktCommand(name = "tasks") {
    val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "List all tasks in the project and their dependencies"

    override suspend fun run() = withBackend(commonOptions, commandName) { backend ->
        backend.showTasks()
    }
}

private class ModulesCommand : SuspendingCliktCommand(name = "modules") {
    val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "List all modules in the project"

    override suspend fun run() = withBackend(commonOptions, commandName) { backend ->
        backend.showModules()
    }
}

private class TestCommand : SuspendingCliktCommand(name = "test") {
    val platform by platformOption()
    val filter by option("-f", "--filter", help = "wildcard filter to run only matching tests, the option could be repeated to run tests matching any filter")
    val includeModules by option("-m", "--include-module", help = "specific module to check, the option could be repeated to check several modules").multiple()
    val excludeModules by option("--exclude-module", help = "specific module to exclude, the option could be repeated to exclude several modules").multiple()
    val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "Run tests in the project"

    override suspend fun run() {
        if (filter != null) {
            userReadableError("Filters are not implemented yet")
        }

        // try to execute as many tests as possible
        withBackend(
            commonOptions,
            commandName,
            taskExecutionMode = TaskExecutor.Mode.GREEDY,
        ) { backend ->
            backend.test(
                platforms = platform.ifEmpty { null }?.toSet(),
                includeModules = if (includeModules.isNotEmpty()) includeModules.toSet() else null,
                excludeModules = excludeModules.toSet(),
            )
        }
    }
}

private class PublishCommand : SuspendingCliktCommand(name = "publish") {
    val module by option("-m", "--modules", help = "specify modules to publish, delimited by ','. " +
            "By default 'publish' command will publish all possible modules").split(",")
    val commonOptions by requireObject<RootCommand.CommonOptions>()
    val repositoryId by argument("repository-id")

    override fun help(context: Context): String = "Publish modules to a repository"

    override suspend fun run() {
        withBackend(commonOptions, commandName) { backend ->
            backend.publish(
                modules = module?.toSet(),
                repositoryId = repositoryId,
            )
        }
    }
}

private class ToolCommand : SuspendingCliktCommand(name = "tool") {
    init {
        subcommands(
            JaegerToolCommand(),
            JdkToolCommands(),
            KeystoreToolCommand(),
        )
    }

    override fun help(context: Context): String = "Run a tool"

    override suspend fun run() = Unit
}

private class BuildCommand : SuspendingCliktCommand(name = "build") {
    val platform by platformOption()
    val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "Compile and link all code in the project"

    override suspend fun run() = withBackend(commonOptions, commandName) { backend ->
        backend.build(platforms = if (platform.isEmpty()) null else platform.toSet())
    }
}

private class SettingsCommand: SuspendingCliktCommand(name = "settings") {
    val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "Print the effective Amper settings of each module"

    override suspend fun run() {
        withBackend(commonOptions, commandName) { backend ->
            val terminal = backend.context.terminal

            val model = with(CliProblemReporterContext) {
                SchemaBasedModelImport.getModel(backend.context.projectContext)
            }.get()

            model.modules.forEach { module ->
                if (model.modules.size > 1) {
                    terminal.info("Module: " + module.userReadableName + "\n",
                        Whitespace.PRE_LINE, TextAlign.LEFT)
                }
                val distinctSegments = module.fragments.distinctBy { it.platforms }
                distinctSegments.forEach {
                    if (distinctSegments.size > 1) {
                        terminal.info("settings" + it.platforms.joinToString("+"){it.pretty}.let {
                            if (it.isNotEmpty()) "@$it" else it
                        } + "\n", Whitespace.PRE_LINE, TextAlign.LEFT)
                    }
                    compositeValueTracesInfo(
                        it.settings,
                        null,
                        module.type,
                        it.platforms,
                        TracesPresentation.CLI
                    )?.let {
                        // whitespace ahead is necessary for copypastability
                        terminal.print(Text(it.split("\n").joinToString("\n") { "  $it" }))
                    }
                }
            }
        }
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
    values.map { checkAndGetPlatform(it) }
}

/**
 * Check if the passed value can be converted to a platform and return one, if possible.
 * Throw exception otherwise.
 */
private fun checkAndGetPlatform(value: String) =
    prettyLeafPlatforms[value]
        ?: userReadableError("Unsupported platform '$value'.\n\nPossible values: $prettyLeafPlatformsString")

suspend fun main(args: Array<String>) {
    try {
        TelemetryEnvironment.setup()
        spanBuilder("Root").use {
            RootCommand().main(args)
        }
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
