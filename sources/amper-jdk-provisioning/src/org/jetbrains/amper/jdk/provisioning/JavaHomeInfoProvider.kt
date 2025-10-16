/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.amper.problems.reporting.ProblemReporter
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Allows reading information about the JDK located at the current `JAVA_HOME` path.
 * The information is cached for further invocations in a thread-safe way.
 */
// this is NOT a Kotlin `object` because some consumers may need to reset the cache by creating a new instance
internal class JavaHomeInfoProvider {

    private val mutex = Mutex()
    private var javaHomeInfo: JavaHomeInfo? = null

    /**
     * Gets the [JavaHomeInfo] for the current `JAVA_HOME`, reporting critical problems to the given [problemReporter].
     *
     * The result is cached for further invocations in a thread-safe way, so the problems are reported only once per
     * instance of [JavaHomeInfoProvider].
     */
    context(problemReporter: ProblemReporter)
    suspend fun getOrRead(): JavaHomeInfo = javaHomeInfo ?: mutex.withLock {
        javaHomeInfo ?: withContext(Dispatchers.IO) {
            readJavaHomeInfo().also {
                javaHomeInfo = it
            }
        }
    }
}

internal sealed interface JavaHomeInfo {

    /**
     * This result means that `JAVA_HOME` is not set, or is set to the empty string.
     */
    data object UnsetOrEmpty : JavaHomeInfo

    /**
     * This result means that `JAVA_HOME` is set to a valid JDK or JRE, and that we could read the release information.
     */
    data class Valid(
        val path: Path,
        val releaseInfo: ReleaseInfo,
    ) : JavaHomeInfo

    /**
     * This result means that `JAVA_HOME` is invalid (e.g., invalid path, doesn't exist, is not a directory, etc.).
     *
     * The exact errors are reported via the [ProblemReporter].
     */
    data object Invalid : JavaHomeInfo
}

context(problemReporter: ProblemReporter)
private fun readJavaHomeInfo(): JavaHomeInfo {
    val javaHomeEnv = System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() } ?: return JavaHomeInfo.UnsetOrEmpty
    val javaHomePath = try {
        Path(javaHomeEnv)
    } catch (e: InvalidPathException) {
        problemReporter.reportMessage(InvalidJavaHome(javaHomeEnv, "java.home.invalid.path", e.message))
        return JavaHomeInfo.Invalid
    }
    if (!javaHomePath.exists()) {
        problemReporter.reportMessage(InvalidJavaHome(javaHomeEnv, "java.home.nonexistent.path", javaHomeEnv))
        return JavaHomeInfo.Invalid
    }
    if (!javaHomePath.isDirectory()) {
        problemReporter.reportMessage(InvalidJavaHome(javaHomeEnv, "java.home.not.directory", javaHomeEnv))
        return JavaHomeInfo.Invalid
    }
    val releaseFile = javaHomePath.resolve("release")
    if (!releaseFile.exists()) {
        problemReporter.reportMessage(InvalidJavaHome(javaHomeEnv, "java.home.no.release.file", javaHomeEnv))
        return JavaHomeInfo.Invalid
    }
    val releaseInfo = try {
        releaseFile.readReleaseInfo()
    } catch (e: InvalidReleaseInfoException) {
        problemReporter.reportMessage(InvalidJavaHome(javaHomeEnv, "java.home.invalid.release.file", e.message))
        return JavaHomeInfo.Invalid
    }
    return JavaHomeInfo.Valid(javaHomePath, releaseInfo)
}
