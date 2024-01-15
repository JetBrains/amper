/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.downloader.Downloader
import org.jetbrains.amper.downloader.cleanDirectory
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.PotatoModuleProgrammaticSource
import java.io.File
import kotlin.io.path.pathString

class JvmTestTask(
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val projectRoot: AmperProjectRoot,
    private val module: PotatoModule,
    override val taskName: TaskName,
): Task {
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
        val testClasspath = CommonTaskUtils.buildClasspath(compileTask)

        val junitConsole = Downloader.downloadFileToCacheLocation(junitConsoleUrl, userCacheRoot)

        // TODO settings!
        val jdkHome = JdkDownloader.getJdkHome(userCacheRoot)

        val javaExecutable = JdkDownloader.getJavaExecutable(jdkHome)

        cleanDirectory(taskOutputRoot.path)
        val reportsDir = taskOutputRoot.path.resolve("reports")

        val jvmCommand = listOf(
            javaExecutable.pathString,
            // TODO more jvm options? which options should be here?
            "-ea",
            "-jar",
            junitConsole.pathString,
            "execute",
            // TODO I certainly want to have it here by default (too many real life errors when tests are skipped for a some reason)
            //  but probably there should be an option to disable it
            "--fail-if-no-tests",
            "--scan-class-path",
            "--class-path=${testClasspath.joinToString(File.pathSeparator)}",
            "--reports-dir=${reportsDir}",
        )
//        val jvmCommand = listOf(
//            javaExecutable.pathString,
//            // TODO more jvm options? which options should be here?
//            "-ea",
//            "-cp",
//            (listOf(junitConsole.pathString) + testClasspath).joinToString(File.pathSeparator),
//            "execute",
//            // TODO I certainly want to have it here by default (too many real life errors when tests are skipped for a some reason)
//            //  but probably there should be an option to disable it
//            "--fail-if-no-tests",
//            "--scan-class-path",
//            "--reports-dir=${reportsDir}",
//        )

        // TODO should be customizable?
        val workingDirectory = when (val source = module.source) {
            // same directory as module.yaml
            is PotatoModuleFileSource -> source.buildDir
            // no way of knowing, default to a safe choice
            PotatoModuleProgrammaticSource -> projectRoot.path
        }

        return spanBuilder("junit-platform-console-standalone")
            .setAttribute("junit-platform-console-standalone", junitConsole.pathString)
            .setAttribute("workding-dir", workingDirectory.pathString)
            .setAttribute(AttributeKey.stringArrayKey("tests-classpath"), testClasspath.map { it.pathString })
            .setAttribute(AttributeKey.stringArrayKey("jvm-args"), jvmCommand)
            .useWithScope { span ->
                BuildPrimitives.runProcessAndAssertExitCode(jvmCommand, workingDirectory, span)

                // TODO exit code from junit launcher should be carefully become some kind of exit code for entire Amper run
                //  + one more interesting case: if we reported some failed tests to TeamCity, exit code of Amper should be 0,
                //  since the build will be failed anyway and it'll just have one more useless build failure about exit code

                JvmTestTaskResult(dependenciesResult)
            }
    }

    class JvmTestTaskResult(
        override val dependencies: List<TaskResult>,
    ) : TaskResult
}
