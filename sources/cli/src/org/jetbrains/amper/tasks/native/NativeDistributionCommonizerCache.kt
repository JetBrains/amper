/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("SameParameterValue")

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.compilation.KotlinNativeCompiler
import org.slf4j.LoggerFactory
import java.io.File
import java.io.Serializable
import kotlin.io.path.createFile
import kotlin.io.path.isDirectory


// Copied from https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/native/internal/NativeDistributionCommonizerCache.kt
// to preserve commonizer caching logic (with additional changes).
// Yet it is not an ideal solution, but adding dependency on a related kotlin library is more support consuming.
// See: [NativeDistributionCommonizerLock], [CommonizeNativeDistributionTask]
class NativeDistributionCommonizerCache(private val konan: KotlinNativeCompiler) : Serializable {

    private val commonizedDir by lazy { konan.commonizedPath.toFile() }

    /**
     * Calls [writeCacheAction] for uncached targets and marks them as cached if it succeeds
     */
    suspend fun writeCacheForUncachedTargets(
        outputTargets: Set<String>,
        writeCacheAction: suspend (todoTargets: Set<String>) -> Unit
    ) = lock.withLock {
        val todoOutputTargets = todoTargets(outputTargets)
        if (todoOutputTargets.isEmpty()) return@withLock

        writeCacheAction(todoOutputTargets)

        todoOutputTargets
            .map { outputTarget -> konan.commonizedPath.resolve(outputTarget) }
            .filter { commonizedDirectory -> commonizedDirectory.isDirectory() }
            .forEach { commonizedDirectory -> commonizedDirectory.resolve(".success").createFile() }
    }

    private fun todoTargets(
        outputTargets: Set<String>
    ): Set<String> {
        lock.checkLocked(commonizedDir)
        logInfo("Calculating cache state for $outputTargets")

        val cachedOutputTargets = outputTargets
            .filter { outputTarget -> isCached(konan.commonizedPath.resolve(outputTarget).toFile()) }
            .onEach { outputTarget -> logInfo("Cache hit: $outputTarget already commonized") }
            .toSet()

        val todoOutputTargets = outputTargets - cachedOutputTargets

        if (todoOutputTargets.isEmpty()) {
            logInfo("All available targets are commonized already - Nothing to do")
            if (todoOutputTargets.isNotEmpty()) {
                logInfo("Platforms cannot be commonized, because of missing platform libraries: $todoOutputTargets")
            }

            return emptySet()
        }

        return todoOutputTargets
    }

    private fun isCached(directory: File): Boolean {
        val successMarkerFile = directory.resolve(".success")
        return successMarkerFile.isFile
    }

    /**
     * Re-entrant lock implementation capable of locking a given output directory
     * even between multiple processes.
     */
    @Transient
    private var lock = NativeDistributionCommonizerLock(commonizedDir, ::logInfo)

    private fun logInfo(message: String) = logger.info(message)

    private val logger = LoggerFactory.getLogger(javaClass)

}
