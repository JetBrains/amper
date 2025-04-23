/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.files

import org.jetbrains.amper.concurrency.produceResultWithDoubleLock
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Create a target file once and reuse it as a result of further invocations until its hash is valid.
 *
 * Method could be used safely from different JVM threads and from different processes.
 *
 * To achieve such concurrency safety, it does the following:
 * - creates a temporary lock file inside the temp directory (resolved with the help of given lambda [tempDir]),
 * - takes JVM and inter-process locks on that file
 * - creates a temporary file inside temp directory
 * - under the lock write content to that file with help of the given lambda ([writeFileContent])
 * - after the file was successfully written to temp location, its sha1 hash is stored into the target file directory
 * - finally, temporary file is moved to the target file location.
 *
 * The following two restrictions are applied on the input parameters for correct locking logic:
 *  - __MUST__ the same temporary directory is used for the given target file no matter
 *      how many times the method is called;
 *  - __SHOULD__ different target files with the same file names correspond to different temp directories
 *      (if this is not met, some contention might be observed during downloading of such files)
 *
 * Note: Since the first lock is a non-reentrant coroutine [kotlinx.coroutines.sync.Mutex],
 *  callers MUST not call locking methods defined in this utility file again from inside the lambda
 *  ([writeFileContent]) - that would lead to the hanging coroutine.
 */
suspend fun produceFileWithDoubleLockAndHash(
    target: Path,
    tempDir: suspend () -> Path = {
        // todo (AB) : Add path checksum to avoid contention if different files have the same name but different paths.
        Path(System.getProperty("java.io.tmpdir")).resolve(".amper")
    },
    writeFileContent: suspend (Path, FileChannel) -> Boolean
): Path? = produceResultWithDoubleLock(
    tempDir(),
    target.name,
    getAlreadyProducedResult = {
        // todo (AB) : replace with logic from ExecuteOnChange (hashes are too heavy)
        if (!target.exists()) {
            null
        } else {
            val hashFile = target.parent.resolve("${target.name}.sha1")
            if (!hashFile.exists()) {
                null
            } else {
                val expectedHash = hashFile.readText()
                val actualHash = computeHash(target, "sha1").hash

                if (expectedHash != actualHash) {
                    null
                } else {
                    target
                }
            }
        }
    }
) { tempFilePath, fileChannel ->
    val isSuccessfullyWritten = writeFileContent(tempFilePath, fileChannel)
        .also {
            if (it) {
                val hashFile = target.parent.resolve("${target.name}.sha1")
                // Store sha1 of resulted sourceSet file
                val hasher = Hasher("sha1")
                fileChannel.position(0)
                fileChannel.readTo(listOf(hasher.writer))
                hashFile.parent.createDirectories()
                hashFile.writeText(hasher.hash)
            }
        }

    if (isSuccessfullyWritten) {
        target.parent.createDirectories()
        tempFilePath.moveTo(target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    target.takeIf { isSuccessfullyWritten }
}
