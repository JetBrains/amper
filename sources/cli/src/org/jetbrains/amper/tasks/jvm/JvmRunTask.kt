/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.PotatoModuleProgrammaticSource
import org.jetbrains.amper.jvm.findEffectiveJvmMainClass
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.CommonTaskUtils
import org.jetbrains.amper.tasks.RunTask
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.io.path.pathString

class JvmRunTask(
    override val taskName: TaskName,
    override val module: PotatoModule,
    private val userCacheRoot: AmperUserCacheRoot,
    private val projectRoot: AmperProjectRoot,
    private val commonRunSettings: CommonRunSettings,
) : RunTask {
    override val platform = Platform.JVM
    override val buildType: BuildType
        get() = BuildType.Debug

    private val fragments = module.fragments.filter { !it.isTest && it.platforms.contains(Platform.JVM) }

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        DeadLockMonitor.disable()

        val effectiveMainClassFqn = fragments.findEffectiveJvmMainClass()
            ?: error("Main Class was not found for ${module.userReadableName} in any of the following source directories:\n" +
                    fragments.joinToString("\n") { it.src.pathString })

        val compileTaskResult = dependenciesResult.filterIsInstance<JvmCompileTask.TaskResult>().singleOrNull()
            ?: error("Could not find a single compile task in dependencies of $taskName")

        val javaExecutable = JdkDownloader.getJdk(userCacheRoot).javaExecutable

        // TODO how to support options like debugging, xmx etc?
        // TODO some of them should be coming from module files, some from command line
        // ideally ./amper :cli:jvmRun --debug

        val classpathList = CommonTaskUtils.buildRuntimeClasspath(compileTaskResult)
        val classpath = classpathList.joinToString(File.pathSeparator)

        // TODO how to customize properties? -ea? -Xmx?
        val jvmArgs = listOf("-ea")

        val args = listOf(javaExecutable.pathString) +
                jvmArgs +
                listOf(
                    "-cp",
                    classpath,
                    effectiveMainClassFqn,
                ) +
                commonRunSettings.programArgs

        return spanBuilder("jvm-run")
            .setAttribute("java", javaExecutable.pathString)
            .setListAttribute("jvm-args", jvmArgs)
            .setListAttribute("program-args", commonRunSettings.programArgs)
            .setListAttribute("args", args)
            .setAttribute("classpath", classpath)
            .setAttribute("main-class", effectiveMainClassFqn).useWithScope { span ->
                val workingDir = when (val source = module.source) {
                    is PotatoModuleFileSource -> source.moduleDir
                    PotatoModuleProgrammaticSource -> projectRoot.path
                }

                val result = BuildPrimitives.runProcessAndGetOutput(args, workingDir, span)

                val message = "Process exited with exit code ${result.exitCode}" +
                        (if (result.stderr.isNotEmpty()) "\nSTDERR:\n${result.stderr}\n" else "") +
                        (if (result.stdout.isNotEmpty()) "\nSTDOUT:\n${result.stdout}\n" else "")
                if (result.exitCode != 0) {
                    logger.error(message)
                } else {
                    logger.info(message)
                }

                // TODO Should non-zero exit code fail the task somehow?

                object : TaskResult {
                    override val dependencies: List<TaskResult> = dependenciesResult
                }
            }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
