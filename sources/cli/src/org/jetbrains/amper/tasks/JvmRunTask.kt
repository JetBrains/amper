/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.PotatoModuleProgrammaticSource
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.walk

class JvmRunTask(
    override val taskName: TaskName,
    override val module: PotatoModule,
    private val userCacheRoot: AmperUserCacheRoot,
    private val projectRoot: AmperProjectRoot,
    private val commonRunSettings: CommonRunSettings,
) : RunTask {
    override val platform = Platform.JVM

    private val fragments = module.fragments.filter { !it.isTest && it.platforms.contains(Platform.JVM) }

    // TODO what if several fragments have a main class?
    private val mainClassName = fragments.firstNotNullOfOrNull { it.settings.jvm.mainClass }

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val mainClassReal = if (mainClassName != null) {
            // explicitly defined in module files
            mainClassName
        } else {
            // TODO what if several fragments have main.kt?
            val discoveredMainClass = fragments.firstNotNullOfOrNull { findEntryPoint(it) }
                ?: error("Main Class is not found for ${module.userReadableName} at: " +
                        fragments.joinToString(" ") { it.src.pathString })
            discoveredMainClass
        }

        val compileTaskResult = dependenciesResult.filterIsInstance<JvmCompileTask.TaskResult>().singleOrNull()
            ?: error("Could not find a single compile task in dependencies of $taskName")

        val jdkHome = JdkDownloader.getJdkHome(userCacheRoot)
        val javaExecutable = JdkDownloader.getJavaExecutable(jdkHome)

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
                    mainClassReal,
                ) +
                commonRunSettings.programArgs

        return spanBuilder("jvm-run")
            .setAttribute("java", javaExecutable.pathString)
            .setListAttribute("jvm-args", jvmArgs)
            .setListAttribute("program-args", commonRunSettings.programArgs)
            .setListAttribute("args", args)
            .setAttribute("classpath", classpath)
            .setAttribute("main-class", mainClassReal).useWithScope {
                val workingDir = when (val source = module.source) {
                    is PotatoModuleFileSource -> source.buildDir
                    PotatoModuleProgrammaticSource -> projectRoot.path
                }

                val result = BuildPrimitives.runProcessAndGetOutput(args, workingDir)

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

    // TODO this not how an entry point should be discovered, but whatever for now
    @OptIn(ExperimentalPathApi::class)
    private fun findEntryPoint(fragment: Fragment): String? {
        if (!fragment.src.isDirectory()) {
            return null
        }

        val implicitMainFile = fragment.src.walk(PathWalkOption.BREADTH_FIRST)
            .find { it.name.equals("main.kt", ignoreCase = true) }
            ?.normalize()
            ?.toAbsolutePath()

        if (implicitMainFile == null) {
            return null
        }

        val packageRegex = "^package\\s+([\\w.]+)".toRegex(RegexOption.MULTILINE)
        val pkg = packageRegex.find(implicitMainFile.readText())?.let { it.groupValues[1].trim() }

        val prefix = if (pkg != null) "${pkg}." else ""
        val result = "${prefix}MainKt"

        return result
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
