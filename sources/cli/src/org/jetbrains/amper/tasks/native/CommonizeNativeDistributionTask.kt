/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.downloadNativeCompiler
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.useWithScope
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.slf4j.LoggerFactory
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString


/**
 * Commonizer task that is used solely for IDE import.
 */
class CommonizeNativeDistributionTask(
    private val model: Model,
    private val userCacheRoot: AmperUserCacheRoot,
    executeOnChangedInputs: ExecuteOnChangedInputs,
) : Task {
    companion object {
        val TASK_NAME = TaskName("commonizeNativeDistribution")
    }

    override val taskName = TASK_NAME

    private val kotlinDownloader = KotlinArtifactsDownloader(userCacheRoot, executeOnChangedInputs)

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val kotlinVersion = UsedVersions.kotlinVersion

        val sharedPlatformSets = model
            .modules
            .flatMap { it.fragments }
            .map { it.platforms }
            .filter { it.isNotEmpty() }
            // Filter out all leaf fragments.
            .filterNot { it.singleOrNull()?.isLeaf == true }
            .toSet()

        val sharedPlatforms = sharedPlatformSets.map {
            it.joinToString(prefix = "(", separator = ",", postfix = ")") { it.name.lowercase() }
        }.toSet()

        // TODO Maybe this should be separated into something more than a suspend function.
        val compiler = downloadNativeCompiler(kotlinVersion, userCacheRoot)
        val cache = NativeDistributionCommonizerCache(compiler)
        val commonizerClasspath = kotlinDownloader.downloadKotlinCommonizerEmbeddable(kotlinVersion)
        // TODO Settings.
        val jdk = JdkDownloader.getJdk(userCacheRoot)

        cache.writeCacheForUncachedTargets(sharedPlatforms) { todoOutputTargets ->
            val commonizerArgs = buildList {
                add("native-dist-commonize")
                add("-distribution-path"); add(compiler.kotlinNativeHome.absolutePathString())
                add("-output-path"); add(compiler.commonizedPath.absolutePathString())
                add("-output-targets"); add(todoOutputTargets.joinToString(separator = ";"))
            }

            // TODO Add caching.
            spanBuilder("kotlin-native-distribution-commonize")
                .setAttribute("compiler-version", kotlinVersion)
                .setListAttribute("commonizer-args", commonizerArgs)
                .useWithScope {
                    logger.info("Calling Kotlin commonizer...")
                    val result = jdk.runJava(
                        workingDir = Path("."),
                        mainClass = "org.jetbrains.kotlin.commonizer.cli.CommonizerCLI",
                        classpath = commonizerClasspath,
                        programArgs = commonizerArgs,
                        jvmArgs = listOf(),
                        outputListener = LoggingProcessOutputListener(logger),
                    )
                    if (result.exitCode != 0) {
                        userReadableError("Kotlin commonizer invocation (see errors above)")
                    }
                }
        }

        return EmptyTaskResult
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
