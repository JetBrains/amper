/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.google.common.hash.Hashing
import com.sun.jna.Platform
import one.profiler.AsyncProfiler
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.util.OS
import org.slf4j.LoggerFactory
import kotlin.io.path.createDirectories
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes

object AsyncProfilerMode {
    fun attachAsyncProfiler(logsDir: AmperBuildLogsRoot, buildOutputRoot: AmperBuildOutputRoot) {
        val platformId = getPlatformId()
        val ext = when (OS.type) {
            OS.Type.Windows -> ".dll"
            OS.Type.Linux -> ".so"
            OS.Type.Mac -> ".dylib"
        }
        val name = "libasyncProfiler$ext"

        val resourceName = "binaries/$platformId/$name"
        val resourceStream = javaClass.classLoader.getResourceAsStream(resourceName)
            ?: error("Resource '$resourceName' is not found in Amper classpath")
        val libBytes = resourceStream.use { stream -> stream.readAllBytes() }
        val libSha256 = Hashing.sha256().hashBytes(libBytes).toString()

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
        val arch = Platform.ARCH
        return when (OS.type) {
            OS.Type.Mac -> "macos"

            OS.Type.Windows -> when (arch) {
                "x86-64" -> "windows"
                "aarch64" -> "windows-aarch64"
                else -> error("Unsupported Windows arch: $arch")
            }

            OS.Type.Linux -> when (arch) {
                "x86-64" -> "linux"
                "aarch64" -> "linux-aarch64"
                else -> error("Unsupported Linux arch: $arch")
            }
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
