/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import one.profiler.AsyncProfiler
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.stdlib.hashing.sha256String
import org.slf4j.LoggerFactory
import kotlin.io.path.createDirectories
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes

object AsyncProfilerMode {
    fun attachAsyncProfiler(logsDir: AmperBuildLogsRoot, buildOutputRoot: AmperBuildOutputRoot) {
        val platformId = getPlatformId()
        val ext = when (OsFamily.Companion.current) {
            OsFamily.Windows -> ".dll"
            OsFamily.Linux, OsFamily.FreeBSD, OsFamily.Solaris -> ".so"
            OsFamily.MacOs -> ".dylib"
        }
        val name = "libasyncProfiler$ext"

        val resourceName = "binaries/$platformId/$name"
        val resourceStream = javaClass.classLoader.getResourceAsStream(resourceName)
            ?: error("Resource '$resourceName' is not found in Amper classpath")
        val libBytes = resourceStream.use { stream -> stream.readAllBytes() }
        val libSha256 = libBytes.sha256String()

        val libFile = buildOutputRoot.path.resolve("libasyncProfiler").resolve(libSha256).resolve(name)
        // do not overwrite file unless absolutely necessary
        // on Windows you can't overwrite an open file or a loaded library
        if (!libFile.isRegularFile() || libFile.fileSize() != libBytes.size.toLong()) {
            cleanDirectory(libFile.parent)
            libFile.writeBytes(libBytes)
        }

        val snapshotFile = logsDir.path.resolve("async-profiler-snapshot.jfr")
            .also { it.parent.createDirectories() }
        val startCommand = "start,file=$snapshotFile,event=wall,interval=10ms,jfr,jfrsync=profile"

        logger.info("Loading async profiler from $libFile, start command: $startCommand")

        val asyncProfiler = AsyncProfiler.getInstance(libFile.pathString)
        asyncProfiler.execute(startCommand)

        logger.info("Async profiler started, snapshot file: $snapshotFile")
    }

    private fun getPlatformId(): String {
        val arch = Arch.Companion.current
        return when (OsFamily.Companion.current) {
            OsFamily.MacOs -> "macos"

            OsFamily.Windows -> when (arch) {
                Arch.X64 -> "windows"
                Arch.Arm64 -> "windows-aarch64"
            }

            OsFamily.Linux, OsFamily.Solaris, OsFamily.FreeBSD -> when (arch) {
                Arch.X64 -> "linux"
                Arch.Arm64 -> "linux-aarch64"
            }
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}