/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString
import kotlin.io.path.walk

class TempDirExtension : Extension, BeforeEachCallback, AfterEachCallback {
    private val pathRef = AtomicReference<Path>(null)

    val path
        get() = pathRef.get()!!

    override fun beforeEach(context: ExtensionContext?) {
        pathRef.set(createTempDirectory(Dirs.tempDir, "test-dir"))
    }

    override fun afterEach(context: ExtensionContext?) {
        val current = pathRef.getAndSet(null)!!
        deleteWithDiagnostics(current)
    }

    companion object {
        private fun deleteWithDiagnostics(path: Path) {
            try {
                // On Windows, directory locks might not be released instantly, so we allow some extra time
                path.deleteRecursivelyWithRetries()
            } catch (e: IOException) {
                // Selectively unlock specific files (from Android build) only as an exceptional case
                // It's important to prioritize detecting these issues rather than silently ignoring them
                // Any ignoring should be an explicit decision
                if (DefaultSystemInfo.detect().family.isWindows) {
                    // There is little we can do about the Gradle daemon still holding some files after delegated
                    // Android builds (we already use internal APIs to try to close it). Since the daemon doesn't want
                    // to stop, we find the remaining files from the delegated Gradle build and unlock them by hand.
                    // This is an exceptional case. In other cases, we want to know about leaked processes and fix
                    // those. Unlocking all files would hide some real issues.
                    killProcessesLockingAndroidBuildFiles(path)
                    path.deleteRecursivelyWithRetries()
                    return
                }
                throw e
            }
        }

        /**
         * Attempts to delete this directory, and retries multiple times in case of [IOException].
         * This is useful on Windows because files may stay inaccessible for a little bit after releasing a lock.
         *
         * If the error persists, it is rethrown. Partial deletion may have happened in this case.
         * All deletion errors can be found as suppressed exceptions of the thrown [IOException].
         */
        private fun Path.deleteRecursivelyWithRetries() {
            var lastException: IOException? = null
            repeat(100) {
                try {
                    deleteRecursively()
                    return
                } catch (e: IOException) {
                    lastException = e
                }
                Thread.sleep(100)
            }
            throw lastException ?: error("The retry loop has ended but we have no exception")
        }

        private val androidGradleBuildPathRegex = Regex(""".*\\build\\tasks\\[^\\]+\\gradle-project\\build\\.*""")

        private fun killProcessesLockingAndroidBuildFiles(path: Path) {
            path.walk()
                .filter { androidGradleBuildPathRegex.matches(it.pathString) }
                .forEach { WindowsProcessHelper.unlockFile(it) }
        }
    }
}
