/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.downloader.Downloader
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.PotatoModuleProgrammaticSource
import org.jetbrains.amper.tasks.CommonTaskUtils
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.TestTask
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import kotlin.io.path.deleteExisting
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.writeText

@Suppress("BlockingMethodInNonBlockingContext")
class JvmTestTask(
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val projectRoot: AmperProjectRoot,
    private val terminal: Terminal,
    override val module: PotatoModule,
    override val taskName: TaskName,
): TestTask {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val platform: Platform = Platform.JVM

    override suspend fun run(dependenciesResult: List<TaskResult>): JvmTestTaskResult {
        // https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.1/junit-platform-console-standalone-1.10.1.jar
        val junitConsoleUrl = Downloader.getUriForMavenArtifact(
            mavenRepository = "https://repo1.maven.org/maven2",
            groupId = "org.junit.platform",
            artifactId = "junit-platform-console-standalone",
            packaging = "jar",
            version = "1.10.1",
        ).toString()

        // test task depends on compile test task
        val compileTask = dependenciesResult.filterIsInstance<JvmCompileTask.TaskResult>().singleOrNull()
            ?: error("JvmCompileTask result it not found in dependencies")
        if (compileTask.classesOutputRoot.listDirectoryEntries().isEmpty()) {
            logger.warn("No test classes, skipping test execution for module '${module.userReadableName}'")
            return JvmTestTaskResult(dependenciesResult)
        }

        val testClasspath = CommonTaskUtils.buildRuntimeClasspath(compileTask)
        val testModuleClasspath = compileTask.classesOutputRoot

        val junitConsole = Downloader.downloadFileToCacheLocation(junitConsoleUrl, userCacheRoot)

        // TODO settings!
        val javaExecutable = JdkDownloader.getJdk(userCacheRoot).javaExecutable

        cleanDirectory(taskOutputRoot.path)
        val reportsDir = taskOutputRoot.path.resolve("reports")

        val junitArgs = listOf(
            // TODO I certainly want to have it here by default (too many real life errors when tests are skipped for a some reason)
            //  but probably there should be an option to disable it
            "--fail-if-no-tests",
            "--scan-class-path=$testModuleClasspath",
            "--class-path=${testClasspath.joinToString(File.pathSeparator)}",
            "--reports-dir=${reportsDir}",
        )

        val junitArgsFile = Files.createTempFile(tempRoot.path, "junit-args-", ".txt")
        junitArgsFile.writeText(junitArgs.joinToString("\n") { arg ->
            "\"${arg.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        })

        try {
            val jvmCommand = listOf(
                javaExecutable.pathString,
                // TODO more jvm options? which options should be here?
                "-ea",
                "-jar",
                junitConsole.pathString,
                "execute",
                "@$junitArgsFile",
            )

            // TODO should be customizable?
            val workingDirectory = when (val source = module.source) {
                // same directory as module.yaml
                is PotatoModuleFileSource -> source.moduleDir
                // no way of knowing, default to a safe choice
                PotatoModuleProgrammaticSource -> projectRoot.path
            }

            return spanBuilder("junit-platform-console-standalone")
                .setAttribute("junit-platform-console-standalone", junitConsole.pathString)
                .setAttribute("working-dir", workingDirectory.pathString)
                .setListAttribute("tests-classpath", testClasspath.map { it.pathString })
                .setListAttribute("jvm-args", jvmCommand)
                .setListAttribute("junit-args", junitArgs)
                .useWithScope { span ->
                    DeadLockMonitor.disable()

                    val result = BuildPrimitives.runProcessAndGetOutput(jvmCommand, workingDirectory, span, printOutputToTerminal = terminal)

                    // TODO exit code from junit launcher should be carefully become some kind of exit code for entire Amper run
                    //  + one more interesting case: if we reported some failed tests to TeamCity, exit code of Amper should be 0,
                    //  since the build will be failed anyway and it'll just have one more useless build failure about exit code
                    if (result.exitCode != 0) {
                        val meaning = if (result.exitCode == 2) " (no tests were discovered)" else ""
                        userReadableError("JVM tests failed for module '${module.userReadableName}' with exit code ${result.exitCode}$meaning (see errors above)")
                    }

                    JvmTestTaskResult(dependenciesResult)
                }
        } finally {
            junitArgsFile.deleteExisting()
        }
    }

    class JvmTestTaskResult(
        override val dependencies: List<TaskResult>,
    ) : TaskResult
}
