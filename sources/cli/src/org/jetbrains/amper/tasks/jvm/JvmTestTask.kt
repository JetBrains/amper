/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jvm.JdkDownloader
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.TestResultsFormat
import org.jetbrains.amper.tasks.TestTask
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.test.FilterMode
import org.jetbrains.amper.test.TestFilter
import org.jetbrains.amper.test.wildcardsToRegex
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.writeText

class JvmTestTask(
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val projectRoot: AmperProjectRoot,
    private val buildOutputRoot: AmperBuildOutputRoot,
    private val terminal: Terminal,
    private val commonRunSettings: CommonRunSettings,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    override val module: AmperModule,
    override val taskName: TaskName,
    override val platform: Platform = Platform.JVM,
): TestTask {

    init {
        require(platform == Platform.JVM || platform == Platform.ANDROID) {
            "Illegal platform for JvmTestTask: $platform"
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun run(dependenciesResult: List<TaskResult>): Result {
        // test task depends on compile test task
        val compileTask = dependenciesResult.filterIsInstance<JvmCompileTask.Result>().singleOrNull()
            ?: error("${JvmCompileTask::class.simpleName} result is not found in dependencies")
        if (compileTask.classesOutputRoot.listDirectoryEntries().isEmpty()) {
            logger.warn("No test classes, skipping test execution for module '${module.userReadableName}'")
            return Result()
        }

        // test task depends on test jvm classpath task
        val jvmRuntimeClasspathTask = dependenciesResult.filterIsInstance<JvmRuntimeClasspathTask.Result>().singleOrNull()
            ?: error("${JvmRuntimeClasspathTask::class.simpleName} result is not found in dependencies")

        val testClasspath = jvmRuntimeClasspathTask.jvmRuntimeClasspath
        val testModuleClasspath = compileTask.classesOutputRoot

        // TODO use maven instead of packing this in the distribution?
        val amperJUnitLauncherLib = extractJUnitLauncherClasspath()

        // TODO settings!
        val javaExecutable = JdkDownloader.getJdk(userCacheRoot).javaExecutable

        cleanDirectory(taskOutputRoot.path)

        val reportsDir = buildOutputRoot.path / "reports" / module.userReadableName / platform.schemaValue
        cleanDirectory(reportsDir)

        val testLauncherArgs = buildList {
            add("--test-runtime-classpath=${testClasspath.joinToString(File.pathSeparator)}")
            add("--test-discovery-classpath=$testModuleClasspath")
            addAll(commonRunSettings.testFilters.map { it.toJUnitArgument() })

            val testFormat = when (commonRunSettings.testResultsFormat) {
                TestResultsFormat.Pretty -> "pretty"
                TestResultsFormat.TeamCity -> "teamcity"
            }
            add("--format=$testFormat")
            add("--reports-dir=${reportsDir}")

            // We don't use inherited IO when started the test launcher process, so the mordant Terminal library
            // inside the test launcher cannot detect the supported features of the current console.
            // This is why we currently just "transfer" the detected features via CLI arguments.
            // Using ProcessBuilder.inheritIO() would make any auto-detection in the test launcher work, but then the
            // test launcher output doesn't mix well with our task progress renderer.
            add("--ansi-level=${terminal.terminalInfo.ansiLevel}")
            add("--ansi-hyperlinks=${if (terminal.terminalInfo.ansiHyperLinks) "on" else "off"}")
        }

        val testLauncherArgsFile = createTempFile(tempRoot.path, "junit-args-", ".txt")
        testLauncherArgsFile.writeText(testLauncherArgs.joinToString("\n") { arg ->
            "\"${arg.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        })

        try {
            val isTeamcityFormat = commonRunSettings.testResultsFormat == TestResultsFormat.TeamCity
            // TODO also support JVM system properties from module files (AMPER-3253), and maybe other options?
            val jvmCommand = listOfNotNull(
                javaExecutable.pathString,
                "-ea",
                "-Djunit.platform.output.capture.stdout=true".takeIf { isTeamcityFormat }, // TODO should it be configurable?
                "-Djunit.platform.output.capture.stderr=true".takeIf { isTeamcityFormat }, // TODO should it be configurable?
                *commonRunSettings.userJvmArgs.toTypedArray(),
                "-cp",
                "${amperJUnitLauncherLib.pathString}${File.separator}*",
                "org.jetbrains.amper.junit.launcher.MainKt",
                "@$testLauncherArgsFile",
            )

            // TODO should be customizable?
            // There is no way of knowing what the working dir should be for generated/unresolved test modules,
            // the project root is a somewhat safe choice.
            val workingDirectory = module.source.moduleDir ?: projectRoot.path

            return spanBuilder("amper-junit-launcher")
                .setAttribute("amper-junit-launcher", amperJUnitLauncherLib.pathString)
                .setAttribute("working-dir", workingDirectory.pathString)
                .setListAttribute("tests-classpath", testClasspath.map { it.pathString })
                .setListAttribute("jvm-args", jvmCommand)
                .setListAttribute("test-launcher-args", testLauncherArgs)
                .use { span ->
                    DeadLockMonitor.disable()

                    val result = BuildPrimitives.runProcessAndGetOutput(
                        workingDir = workingDirectory,
                        command = jvmCommand,
                        span = span,
                        outputListener = PrintToTerminalProcessOutputListener(terminal),
                    )

                    // TODO exit code from junit launcher should be carefully become some kind of exit code for entire Amper run
                    //  + one more interesting case: if we reported some failed tests to TeamCity, exit code of Amper should be 0,
                    //  since the build will be failed anyway and it'll just have one more useless build failure about exit code
                    if (result.exitCode != 0) {
                        val meaning = if (result.exitCode == 2) " (no tests were discovered)" else ""
                        userReadableError("JVM tests failed for module '${module.userReadableName}' with exit code ${result.exitCode}$meaning (see errors above)")
                    }

                    Result()
                }
        } finally {
            testLauncherArgsFile.deleteExisting()
        }
    }

    private suspend fun extractJUnitLauncherClasspath(): Path {
        val launcherDir = userCacheRoot.path.resolve("amper-junit-launcher").createDirectories()
        val classpathList = javaClass.getResource("/junit-launcher/classpath.txt")?.readText()
            ?: error("JUnit launcher classpath is not in the Amper distribution")
        executeOnChangedInputs.execute(
            id = "extract-junit-launcher-classpath",
            configuration = mapOf("junit-launcher-classpath" to classpathList),
            inputs = emptyList(),
        ) {
            cleanDirectory(launcherDir) // we don't want to keep old dependencies that were potentially removed
            classpathList.lines().forEach { jarName ->
                val jarResource = javaClass.getResourceAsStream("/junit-launcher/$jarName")
                    ?: error("Cannot find JUnit launcher jar in resources: $jarName")
                jarResource.use {
                    launcherDir.resolve(jarName).outputStream().use { out ->
                        it.copyTo(out)
                    }
                }
            }
            ExecuteOnChangedInputs.ExecutionResult(outputs = listOf(launcherDir))
        }
        return launcherDir
    }

    class Result : TaskResult
}

private fun TestFilter.toJUnitArgument(): String =
    when (this) {
        is TestFilter.SpecificTestInclude -> "--select-method=${suiteFqn.slashToDollar()}#$testName"
        is TestFilter.SuitePattern -> when (mode) {
            FilterMode.Exclude -> "--exclude-classes=${pattern.slashToDollar().wildcardsToRegex()}"
            FilterMode.Include -> when {
                "*" in pattern || "?" in pattern -> "--include-classes=${pattern.slashToDollar().wildcardsToRegex()}"
                else -> "--select-class=${pattern.slashToDollar()}"
            }
        }
    }

private fun String.slashToDollar() = replace('/', '$')
