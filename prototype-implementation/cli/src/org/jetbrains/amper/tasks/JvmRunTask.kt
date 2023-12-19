/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.TaskName
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.frontend.JvmPart
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.PotatoModuleProgrammaticSource
import org.jetbrains.amper.util.ShellQuoting
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
    private val taskName: TaskName,
    private val module: PotatoModule,
    private val fragment: LeafFragment,
    private val userCacheRoot: AmperUserCacheRoot,
    private val projectRoot: AmperProjectRoot,
) : Task {
    private val mainClassName = fragment.parts.filterIsInstance<JvmPart>().singleOrNull()?.mainClass

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val mainClassReal = if (mainClassName != null) {
            // explicitly defined in module files
            mainClassName
        } else {
            val discoveredMainClass = findEntryPoint(fragment)
                ?: error("Main Class is not found for ${module.userReadableName} at ${fragment.src}")
            discoveredMainClass
        }

        val compileTaskResult = dependenciesResult.filterIsInstance<JvmCompileTask.TaskResult>().singleOrNull()
            ?: error("Could not find a single compile task in dependencies of $taskName")

        val jdkHome = JdkDownloader.getJdkHome(userCacheRoot)
        val javaExecutable = JdkDownloader.getJavaExecutable(jdkHome)

        // TODO how to support options like debugging, xmx etc?
        // TODO some of them should be coming from module files, some from command line
        // ideally ./amper :cli:jvmRun --debug

        val classpathList = CommonTaskUtils.buildClasspath(compileTaskResult)
        val classpath = classpathList.joinToString(File.pathSeparator)

        // TODO how to customize properties? -ea? -Xmx?
        val jvmArgs = listOf("-ea")

        val args = listOf(javaExecutable.pathString) +
                jvmArgs +
                listOf(
                    "-cp",
                    classpath,
                    mainClassReal,
                )

        return spanBuilder("jvm-run")
            .setAttribute("java", javaExecutable.pathString)
            .setAttribute("jvm-args", ShellQuoting.quoteArgumentsPosixShellWay(jvmArgs))
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
    private fun findEntryPoint(fragment: LeafFragment): String? {
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
