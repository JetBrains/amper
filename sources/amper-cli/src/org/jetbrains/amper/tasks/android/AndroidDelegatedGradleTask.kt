/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.android.AndroidBuildRequest
import org.jetbrains.amper.android.AndroidModuleData
import org.jetbrains.amper.android.ResolvedDependency
import org.jetbrains.amper.android.runAndroidBuild
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.api.toStableJsonLikeString
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.jdk.provisioning.JdkDownloader
import org.jetbrains.amper.processes.GradleDaemonShutdownHook
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.toAndroidRequestBuildType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.*
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import kotlin.io.path.exists

abstract class AndroidDelegatedGradleTask(
    private val module: AmperModule,
    private val buildType: BuildType,
    private val incrementalCache: IncrementalCache,
    private val androidSdkPath: Path,
    private val fragments: List<Fragment>,
    private val projectRoot: AmperProjectRoot,
    private val taskOutputPath: TaskOutputRoot,
    private val buildLogsRoot: AmperBuildLogsRoot,
    private val userCacheRoot: AmperUserCacheRoot,
    override val taskName: TaskName,
) : Task {

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val runtimeClasspath = runtimeClasspath(dependenciesResult)

        val moduleGradlePath = module.gradlePath(projectRoot)
        val androidModuleData = AndroidModuleData(
            modulePath = moduleGradlePath,
            moduleClasses = listOf(),
            resolvedAndroidRuntimeDependencies = runtimeClasspath.map {
                ResolvedDependency("group", "artifact", "version", it)
            },
        )
        val request = AndroidBuildRequest(
            root = projectRoot.path,
            phase = phase,
            modules = setOf(androidModuleData),
            buildTypes = setOf(buildType.toAndroidRequestBuildType),
            sdkDir = androidSdkPath,
            targets = setOf(moduleGradlePath),
        )

        val googleServicesFileName = "google-services.json"
        val googleServicesJson = module.source.moduleDir.let { moduleDir ->
            val servicesJsonPath = moduleDir / googleServicesFileName
            if (servicesJsonPath.exists()) servicesJsonPath else null
        }

        val jdk = JdkDownloader.getJdk(userCacheRoot)

        val executionResult = incrementalCache.execute(
            key = taskName.name,
            inputValues = mapOf(
                "jdk.url" to jdk.downloadUrl.toString(),
                "androidConfig" to fragments.joinToString { it.settings.android.toStableJsonLikeString() },
            ),
            inputFiles = runtimeClasspath + additionalInputFiles + (googleServicesJson?.let { listOf(it) } ?: listOf()),
        ) {
            val gradleProjectPath = (taskOutputPath.path / "gradle-project").also { path -> path.createDirectories() }
            googleServicesJson?.let {
                logger.debug("Using google services json at {}", it)
                googleServicesJson.copyTo(gradleProjectPath / googleServicesFileName, overwrite = true)
            }
            logger.debug("Using android sdk at {}", androidSdkPath)
            val logFileName = UUID.randomUUID()
            val gradleLogStdoutPath =
                buildLogsRoot.path / "gradle" / "${this::class.simpleName}-$logFileName.stdout"
            val gradleLogStderrPath =
                buildLogsRoot.path / "gradle" / "${this::class.simpleName}-$logFileName.stderr"
            gradleLogStdoutPath.createParentDirectories()
            GradleDaemonShutdownHook.setupIfNeeded()
            val result = runAndroidBuild(
                request,
                gradleProjectPath,
                gradleLogStdoutPath,
                gradleLogStderrPath,
                eventHandler = { it.handle(gradleLogStdoutPath, gradleLogStderrPath) },
                javaHomeDir = jdk.homeDir,
            )
            IncrementalCache.ExecutionResult(result.filter(::outputFilterPredicate))
        }
        taskOutputPath.path.createDirectories()
        executionResult.outputFiles.map {
            it.copyToRecursively(
                taskOutputPath.path.resolve(it.fileName), followLinks = false, overwrite = true
            )
        }
        return result(executionResult.outputFiles)
    }

    protected abstract val phase: AndroidBuildRequest.Phase

    protected open val additionalInputFiles: List<Path> = listOf()
    protected open fun outputFilterPredicate(path: Path): Boolean = true
    protected open fun result(artifacts: List<Path>): TaskResult = Result(artifacts)
    protected open fun runtimeClasspath(dependenciesResult: List<TaskResult>): List<Path> {
        val runtimeClasspathTaskResult =
            dependenciesResult.filterIsInstance<JvmRuntimeClasspathTask.Result>().singleOrNull()
                ?: error("${JvmRuntimeClasspathTask::class.simpleName} result is not found in dependencies of $taskName")
        val runtimeClasspath = runtimeClasspathTaskResult.jvmRuntimeClasspath
        return runtimeClasspath
    }

    class Result(val artifacts: List<Path>) : TaskResult

    private val logger = LoggerFactory.getLogger(this::class.java)
}
