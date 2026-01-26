/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import one.profiler.AsyncProfiler
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.stdlib.hashing.sha256String
import org.jetbrains.amper.system.info.Arch
import org.jetbrains.amper.system.info.OsFamily
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.div
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes

internal object Profiler {

    /**
     * Starts the Async Profiler, extracting the native library to the [userCacheRoot] if it wasn't done before.
     * The snapshot file will be placed in the build logs directory defined by [logsRoot].
     */
    fun start(userCacheRoot: AmperUserCacheRoot, logsRoot: AmperBuildLogsRoot) {
        start(userCacheRoot, logsRoot.path.resolve("async-profiler-snapshot.jfr"))
    }

    /**
     * Starts the Async Profiler, extracting the native library to the [userCacheRoot] if it wasn't done before.
     * The results will be written to the given [snapshotFile].
     */
    private fun start(userCacheRoot: AmperUserCacheRoot, snapshotFile: Path) {
        val profiler = getInstanceWithCachedLib(userCacheRoot = userCacheRoot)
        val snapshotFile = snapshotFile.absolute()
        val startCommand = "start,file=${snapshotFile.pathString},event=wall,interval=10ms,jfr,jfrsync=profile"
        logger.info("Starting async profiler with command: $startCommand")
        profiler.execute(startCommand)
        logger.info("Async profiler started, snapshot file: $snapshotFile")
    }

    private fun getInstanceWithCachedLib(userCacheRoot: AmperUserCacheRoot): AsyncProfiler {
        val name = getLibFilename()
        val resourceName = "binaries/${getPlatformId()}/$name"
        val resourceStream = javaClass.classLoader.getResourceAsStream(resourceName)
            ?: error("Resource '$resourceName' is not found in Amper classpath")

        val libBytes = resourceStream.use { stream -> stream.readAllBytes() }
        val libSha256 = libBytes.sha256String()

        val libFile = userCacheRoot.path / "libasyncProfiler" / libSha256 / name
        // do not overwrite file unless absolutely necessary
        // on Windows you can't overwrite an open file or a loaded library
        if (!libFile.isRegularFile() || libFile.fileSize() != libBytes.size.toLong()) {
            logger.info("Extracting async profiler lib to $libFile (only for the first run)")
            cleanDirectory(libFile.parent)
            libFile.writeBytes(libBytes)
        }
        logger.info("Loading async profiler from $libFile")
        return AsyncProfiler.getInstance(libFile.pathString)
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}

private fun getLibFilename(): String {
    val ext = when (OsFamily.current) {
        OsFamily.Windows -> ".dll"
        OsFamily.Linux, OsFamily.FreeBSD, OsFamily.Solaris -> ".so"
        OsFamily.MacOs -> ".dylib"
    }
    return "libasyncProfiler$ext"
}

private fun getPlatformId(): String = when (OsFamily.current) {
    OsFamily.MacOs -> "macos"
    OsFamily.Windows -> when (Arch.current) {
        Arch.X64 -> "windows"
        Arch.Arm64 -> "windows-aarch64"
    }
    OsFamily.Linux,
    OsFamily.Solaris,
    OsFamily.FreeBSD -> when (Arch.current) {
        Arch.X64 -> "linux"
        Arch.Arm64 -> "linux-aarch64"
    }
}
