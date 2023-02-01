/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.TaskName
import org.jetbrains.amper.cli.downloadAndExtractKotlinCompiler
import org.jetbrains.amper.downloader.cleanDirectory
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

class KotlinCompileTask(private val module: PotatoModule, private val fragment: LeafFragment, private val userCacheRoot: AmperUserCacheRoot, private val tempRoot: AmperProjectTempRoot, private val taskOutputRoot: TaskOutputRoot, private val taskName: TaskName, private val executeOnChangedInputs: ExecuteOnChangedInputs): Task {
    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        println("compile ${module.userReadableName} -- ${fragment.name}")

        val mavenDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.TaskResult>()
            .singleOrNull()
            ?: error("Expected one and only one dependencies (${ResolveExternalDependenciesTask::class.java.simpleName}) input, but got: ${dependenciesResult.joinToString { it.task.javaClass.simpleName }}")

        val immediateDependencies = dependenciesResult.filterIsInstance<TaskResult>()

        // TODO resources need not to be compiled here at all since they do not require to be processed
        // so we can just include them to resulting classpath or whatever

        if (!fragment.src.exists()) {
            println("WARN: sources are missing at '${fragment.src}'. Assuming no compilation is required")
            return TaskResult(
                task = this,
                classesRoot = null,
                dependencies = dependenciesResult,
                resourcesRoot = fragment.resourcesPath.takeIf { it.isDirectory() },
            )
        }

        val kotlinVersion = "1.9.20"

        val classpath = immediateDependencies.mapNotNull { it.classesRoot } + mavenDependencies.classpath

        val configuration = mapOf(
            "jdk.version" to JdkDownloader.JBR_SDK_VERSION,
            "kotlin.version" to kotlinVersion,
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val inputs = listOf(fragment.src) + classpath
        executeOnChangedInputs.execute(taskName.toString(), configuration, inputs) {
            // TODO settings!
            val jdkHome = JdkDownloader.getJdkHome(userCacheRoot)

            // TODO settings!
            val kotlinHome = downloadAndExtractKotlinCompiler(kotlinVersion, userCacheRoot)

            val args = listOf(
                "-verbose",
                "-classpath", classpath.joinToString(File.pathSeparator),
                "-jdk-home", jdkHome.pathString,
                "-no-stdlib",
                "-jvm-target", "17",
                "-d", taskOutputRoot.path.pathString,
                fragment.src.pathString,
            )

            tempRoot.path.createDirectories()
            val argFile = Files.createTempFile(tempRoot.path, "kotlin-args-", ".txt")

            // TODO proper escaping with tests
            argFile.writeText(args.joinToString(" ") { "\"${it.replace("\"", "\\\"")}\"" })

            println("kotlin args: ${argFile.readText()}")

            cleanDirectory(taskOutputRoot.path)

            val jvmArgs = listOf(
                JdkDownloader.getJavaExecutable(jdkHome).pathString,
                "-Dkotlin.home=$kotlinHome",
                "-cp",
                (kotlinHome / "lib" / "kotlin-preloader.jar").pathString,
                "org.jetbrains.kotlin.preloading.Preloader",
                "-cp",
                (kotlinHome / "lib" / "kotlin-compiler.jar").pathString,
                "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                "@${argFile}",
            )

            println("jvm args: $jvmArgs")

            val process = ProcessBuilder()
                .command(*jvmArgs.toTypedArray())
                .inheritIO()
                .directory(jdkHome.toFile())
                .start()

            // wrap with cancellation!
            val rc = process.waitFor()
            if (rc != 0) {
                error("kotlinc exited with error code $rc")
            }

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(taskOutputRoot.path))
        }

        return TaskResult(
            task = this,
            classesRoot = taskOutputRoot.path,
            dependencies = dependenciesResult,
            resourcesRoot = fragment.resourcesPath.takeIf { it.isDirectory() },
        )
    }

    class TaskResult(
        override val task: Task,
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val classesRoot: Path?,
        val resourcesRoot: Path?,
    ) : org.jetbrains.amper.tasks.TaskResult
}
