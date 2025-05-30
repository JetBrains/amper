/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.downloadNativeCompiler
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jdk.provisioning.JdkDownloader
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString


/**
 * Commonizer task that is used solely for IDE import.
 */
class CommonizeNativeDistributionTask(
    private val model: Model,
    private val userCacheRoot: AmperUserCacheRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
) : Task {
    companion object {
        val TASK_NAME = TaskName("commonizeNativeDistribution")
    }

    override val taskName = TASK_NAME

    private val kotlinDownloader = KotlinArtifactsDownloader(userCacheRoot, executeOnChangedInputs)

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val kotlinVersion = UsedVersions.kotlinVersion

        val sharedPlatformSets = model.nativePlatformSetsToCommonize()

        val sharedPlatforms = sharedPlatformSets.map { set ->
            set.joinToString(prefix = "(", separator = ",", postfix = ")") { it.nameForCompiler }
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

            spanBuilder("kotlin-native-distribution-commonize")
                .setAttribute("compiler-version", kotlinVersion)
                .setListAttribute("commonizer-args", commonizerArgs)
                .use {
                    executeOnChangedInputs.execute(
                        "native-dist-commonize-$todoOutputTargets",
                        mapOf("commonizerArgs" to commonizerArgs.joinToString()),
                        listOf(
                            compiler.kotlinNativeHome,
                            compiler.commonizedPath,
                            *commonizerClasspath.toTypedArray()
                        )
                    ) {
                        logger.info("Commonizing Kotlin/Native distribution...")
                        val result = jdk.runJava(
                            workingDir = Path("."),
                            mainClass = "org.jetbrains.kotlin.commonizer.cli.CommonizerCLI",
                            classpath = commonizerClasspath,
                            programArgs = commonizerArgs,
                            jvmArgs = listOf(),
                            outputListener = LoggingProcessOutputListener(logger),
                            tempRoot = tempRoot,
                        )
                        if (result.exitCode != 0) {
                            userReadableError("Kotlin commonizer invocation failed (see errors above)")
                        }
                        return@execute ExecuteOnChangedInputs.ExecutionResult(emptyList())
                    }.outputs
                }
        }

        return EmptyTaskResult
    }

    private fun Model.nativePlatformSetsToCommonize(): Set<List<Platform>> {
        val sharedPlatformSets = mutableSetOf<List<Platform>>()
        for (module in modules) {
            for (fragment in module.fragments) {
                val platforms = fragment.platforms.filter { it.isDescendantOf(Platform.NATIVE) }
                if (platforms.size > 1) {
                    sharedPlatformSets += platforms.toList()
                }
            }
        }
        return sharedPlatformSets
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
