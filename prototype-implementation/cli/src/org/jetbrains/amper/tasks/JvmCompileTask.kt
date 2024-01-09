/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.TaskName
import org.jetbrains.amper.cli.downloadAndExtractKotlinCompiler
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.downloader.cleanDirectory
import org.jetbrains.amper.frontend.KotlinPart
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.walk
import kotlin.io.path.writeText

class JvmCompileTask(
    private val module: PotatoModule,
    private val fragment: LeafFragment,
    private val userCacheRoot: AmperUserCacheRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val taskName: TaskName,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
): Task {
    @OptIn(ExperimentalPathApi::class)
    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        logger.info("compile ${module.userReadableName} -- ${fragment.name}")

        val mavenDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.TaskResult>()
            .singleOrNull()
            ?: error("Expected one and only one dependency (${ResolveExternalDependenciesTask.TaskResult::class.java.simpleName}) input, but got: ${dependenciesResult.joinToString { it.javaClass.simpleName }}")

        val immediateDependencies = dependenciesResult.filterIsInstance<TaskResult>()

        val resourcesArePresent = fragment.resourcesPath.isDirectory()
        val sourcesArePresent = fragment.src.exists()

        if (!sourcesArePresent && !resourcesArePresent) {
            logger.warn("sources and resources are missing at '${fragment.src}' and '${fragment.resourcesPath}'. Assuming no compilation step is required")
            return TaskResult(
                classesOutputRoot = null,
                dependencies = dependenciesResult,
            )
        }

        val kotlinPart = fragment.parts.filterIsInstance<KotlinPart>().firstOrNull()
        val languageVersion = kotlinPart?.languageVersion
        val kotlinVersion = "1.9.20"

        val additionalClasspath = dependenciesResult.filterIsInstance<AdditionalClasspathProviderTaskResult>().flatMap { it.classpath }
        val classpath = immediateDependencies.mapNotNull { it.classesOutputRoot } + mavenDependencies.classpath + additionalClasspath

        val configuration: Map<String, String> = mapOf(
            "jdk.version" to JdkDownloader.JBR_SDK_VERSION,
            "kotlin.version" to kotlinVersion,
            "language.version" to (languageVersion ?: ""),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val inputs = listOfNotNull(
            fragment.src.takeIf { sourcesArePresent },
            fragment.resourcesPath.takeIf { resourcesArePresent },
        ) + classpath
        executeOnChangedInputs.execute(taskName.toString(), configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            if (sourcesArePresent) {
                // TODO settings!
                val jdkHome = JdkDownloader.getJdkHome(userCacheRoot)

                // TODO settings!
                val kotlinHome = downloadAndExtractKotlinCompiler(kotlinVersion, userCacheRoot)

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
                    fragment.src.pathString,
                )

                // escaping rules from https://github.com/JetBrains/kotlin/blob/6161f44d91e235750077e1aaa5faff7047316190/compiler/cli/cli-common/src/org/jetbrains/kotlin/cli/common/arguments/preprocessCommandLineArguments.kt#L83
                val argString = args.joinToString(" ") { arg ->
                    if (arg.contains(" ") || arg.contains("'")) {
                        "'${arg.replace("\\", "\\\\").replace("'", "\\'")}'"
                    } else {
                        arg
                    }
                }

                tempRoot.path.createDirectories()
                val argFile = Files.createTempFile(tempRoot.path, "kotlin-args-", ".txt")
                argFile.writeText(argString)

                try {
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
                            logger.info("Calling kotlinc $argString")
                            BuildPrimitives.runProcessAndAssertExitCode(jvmArgs, jdkHome, span)
                        }
                } finally {
                    BuildPrimitives.deleteLater(argFile)
                }

                val javaFilesToCompile = fragment.src.walk()
                    .filter { it.extension == "java" }
                    .map { it.pathString }
                    .toList()
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
                    ) + javaFilesToCompile

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
            }

            if (resourcesArePresent) {
                logger.info("Copy resources from '${fragment.resourcesPath}' to '${taskOutputRoot.path}'")
                BuildPrimitives.copy(
                    from = fragment.resourcesPath,
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
