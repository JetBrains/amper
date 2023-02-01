/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.TaskName
import org.jetbrains.amper.frontend.JvmPart
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.PotatoModule
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.walk

class JvmRunTask(private val taskName: TaskName, private val module: PotatoModule, private val fragment: LeafFragment, private val userCacheRoot: AmperUserCacheRoot): Task {
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

        val compileTaskResult = dependenciesResult.filterIsInstance<KotlinCompileTask.TaskResult>().singleOrNull()
            ?: error("Could not find a single compile task in dependencies of $taskName")

        val jdkHome = JdkDownloader.getJdkHome(userCacheRoot)
        val javaExecutable = JdkDownloader.getJavaExecutable(jdkHome)

        // TODO how to support options like debugging, xmx etc?
        // TODO some of them should be coming from module files, some from command line
        // ideally ./amper :cli:jvmRun --debug

        val classpathList = mutableListOf<Path>()
        buildClasspath(compileTaskResult, classpathList)
        val classpath = classpathList.joinToString(File.pathSeparator)

        val args = listOf(
            javaExecutable.pathString,
            "-cp",
            classpath,
            mainClassReal,
        )
        println("jvm args: $args")

        // make it all cancellable and running in a different context
        // runBlockingCancellable?
        @Suppress("BlockingMethodInNonBlockingContext")

        val process = ProcessBuilder()
            .command(*args.toTypedArray())
            .inheritIO()
            .start()

        @Suppress("BlockingMethodInNonBlockingContext")
        val rc = process.waitFor()

        println("Process exited with exit code $rc")

        return object : TaskResult {
            override val task: Task = this@JvmRunTask
            override val dependencies: List<TaskResult> = dependenciesResult
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

    // TODO this not how classpath should be built, it does not preserve order
    //  also will fail on conflicting dependencies
    //  also it depends on task hierarchy, which could be different from classpath
    //  but for demo it's fine
    private fun buildClasspath(compileTaskResult: KotlinCompileTask.TaskResult, result: MutableList<Path>) {
        val externalClasspath = compileTaskResult.dependencies.filterIsInstance<ResolveExternalDependenciesTask.TaskResult>()
            .flatMap { it.classpath }
        for (path in externalClasspath) {
            if (!result.contains(path)) {
                result.add(path)
            }
        }

        for (depCompileResult in compileTaskResult.dependencies.filterIsInstance<KotlinCompileTask.TaskResult>()) {
            buildClasspath(depCompileResult, result)
        }

        compileTaskResult.resourcesRoot?.let { result.add(it) }
        compileTaskResult.classesRoot?.let { result.add(it) }
    }
}
