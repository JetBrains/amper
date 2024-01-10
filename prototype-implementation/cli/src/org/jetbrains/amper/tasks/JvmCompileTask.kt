/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.KotlinCompilerUtil
import org.jetbrains.amper.cli.TaskName
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.downloader.cleanDirectory
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.KotlinPart
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.ShellQuoting
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlin.io.path.walk

class JvmCompileTask(
    private val module: PotatoModule,
    private val fragments: List<Fragment>,
    private val userCacheRoot: AmperUserCacheRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val taskOutputRoot: TaskOutputRoot,
    override val taskName: TaskName,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
): Task {
    @OptIn(ExperimentalPathApi::class)
    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        logger.info("compile ${module.userReadableName} -- ${fragments.userReadableList()}")

        val mavenDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.TaskResult>()
            .singleOrNull()
            ?: error("Expected one and only one dependency on (${ResolveExternalDependenciesTask.TaskResult::class.java.simpleName}) input, but got: ${dependenciesResult.joinToString { it.javaClass.simpleName }}")

        val immediateDependencies = dependenciesResult.filterIsInstance<TaskResult>()

        // TODO Do we really want different language versions in different fragments?
        val languageVersion = run {
            val kotlinParts = fragments.flatMap { it.parts.filterIsInstance<KotlinPart>() }
            val languageVersions = kotlinParts.mapNotNull { it.languageVersion }.distinct()
            if (languageVersions.size > 1) {
                error("Fragments of module ${module.userReadableName} (${fragments.map { it.name }.sorted().joinToString(" ")}) provide several different kotlin language versions: ${languageVersions.sorted().joinToString(" ")}")
            }
            languageVersions.firstOrNull()
        }

        val kotlinVersion = KotlinCompilerUtil.AMPER_DEFAULT_KOTLIN_VERSION

        val additionalClasspath = dependenciesResult.filterIsInstance<AdditionalClasspathProviderTaskResult>().flatMap { it.classpath }
        val classpath = immediateDependencies.mapNotNull { it.classesOutputRoot } + mavenDependencies.classpath + additionalClasspath

        val configuration: Map<String, String> = mapOf(
            "jdk.version" to JdkDownloader.JBR_SDK_VERSION,
            "kotlin.version" to kotlinVersion,
            "language.version" to (languageVersion ?: ""),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val inputs = fragments.map { it.src } + fragments.map { it.resourcesPath } + classpath

        executeOnChangedInputs.execute(taskName.toString(), configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val presentSources = fragments.map { it.src }.filter { it.exists() }
            if (presentSources.isNotEmpty()) {
                // TODO settings!
                val jdkHome = JdkDownloader.getJdkHome(userCacheRoot)

                // TODO settings!
                val kotlinHome = KotlinCompilerUtil.downloadAndExtractKotlinCompiler(kotlinVersion, userCacheRoot)

                val kotlincOptions = mutableListOf<String>()

                if (languageVersion != null) {
                    kotlincOptions.add("-language-version")
                    kotlincOptions.add(languageVersion)
                }

                kotlincOptions.add("-jvm-target")
                kotlincOptions.add("17")

                val args = kotlincOptions + listOf(
                    "-classpath", classpath.joinToString(File.pathSeparator),
                    "-jdk-home", jdkHome.pathString,
                    "-no-stdlib",
                    "-d", taskOutputRoot.path.pathString,
                ) + presentSources.map { it.pathString }

                KotlinCompilerUtil.withKotlinCompilerArgFile(args, tempRoot) { argFile ->
                    val javaExecutable = JdkDownloader.getJavaExecutable(jdkHome)
                    val jvmArgs = listOf(
                        javaExecutable.pathString,
                        "-Dkotlin.home=$kotlinHome",
                        "-cp",
                        (kotlinHome / "lib" / "kotlin-preloader.jar").pathString,
                        "org.jetbrains.kotlin.preloading.Preloader",
                        "-cp",
                        (kotlinHome / "lib" / "kotlin-compiler.jar").pathString,
                        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                        "@${argFile}",
                    )

                    spanBuilder("kotlinc")
                        .setAttribute("amper-module", module.userReadableName)
                        .setAttribute(AttributeKey.stringArrayKey("jvm-args"), jvmArgs)
                        .setAttribute(AttributeKey.stringArrayKey("args"), args)
                        .setAttribute("version", kotlinVersion)
                        .useWithScope { span ->
                            logger.info("Calling kotlinc ${ShellQuoting.quoteArgumentsPosixShellWay(args)}")
                            BuildPrimitives.runProcessAndAssertExitCode(jvmArgs, jdkHome, span)
                        }
                }

                val javaFilesToCompile = presentSources.flatMap { src ->
                    src.walk().filter { it.extension == "java" }
                }
                if (javaFilesToCompile.isNotEmpty()) {
                    val javac = listOf(
                        JdkDownloader.getJavacExecutable(jdkHome).pathString,
                        "-classpath", (classpath + taskOutputRoot.path.pathString).joinToString(File.pathSeparator),
                        // TODO ok by default?
                        "-encoding", "utf-8",
                        // TODO settings
                        "-g",
                        // https://blog.ltgt.net/most-build-tools-misuse-javac/
                        // we compile module by module, so we don't need javac lookup into other modules
                        "-sourcepath", "", "-implicit:none",
                        "-d", taskOutputRoot.path.pathString,
                    ) + javaFilesToCompile.map { it.pathString }

                    spanBuilder("javac")
                        .setAttribute("amper-module", module.userReadableName)
                        .setAttribute(AttributeKey.stringArrayKey("args"), javac)
                        .setAttribute("jdk-home", jdkHome.pathString)
                        // TODO get version from jdkHome/release
                        // .setAttribute("version", jdkHome.)
                        .useWithScope { span ->
                            BuildPrimitives.runProcessAndAssertExitCode(javac, jdkHome, span)
                        }
                }
            } else {
                logger.info("Sources for fragments (${fragments.userReadableList()}) of module '${module.userReadableName}' are missing, skipping compilation")
            }

            val presentResources = fragments.map { it.resourcesPath }.filter { it.exists() }
            for (resource in presentResources) {
                logger.info("Copy resources from '$resource' to '${taskOutputRoot.path}'")
                BuildPrimitives.copy(
                    from = resource,
                    to = taskOutputRoot.path
                )
            }

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(taskOutputRoot.path))
        }

        return TaskResult(
            classesOutputRoot = taskOutputRoot.path,
            dependencies = dependenciesResult,
        )
    }

    class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val classesOutputRoot: Path?,
    ) : org.jetbrains.amper.tasks.TaskResult

    class AdditionalClasspathProviderTaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val classpath: List<Path>
    ) : org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
