/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.visitFileTree

class TempDirExtension : Extension, BeforeEachCallback, AfterEachCallback {
    private val pathRef = AtomicReference<Path>(null)

    val path
        get() = pathRef.get()!!

    override fun beforeEach(context: ExtensionContext?) {
        pathRef.set(createTempDirectory(TestUtil.tempDir, "test-dir"))
    }

    override fun afterEach(context: ExtensionContext?) {
        val current = pathRef.getAndSet(null)!!
        deleteWithDiagnostics(current)
    }

    companion object {
        fun deleteWithDiagnostics(path: Path) {
            // On Windows, directory locks might not be released instantly, so we allow some extra time
            repeat(20) {
                try {
                    path.deleteRecursively()
                    return
                } catch (_: Throwable) {
                    // ignore exceptions
                }
                Thread.sleep(50)
            }

            val exceptions = mutableListOf<Throwable>()

            @Suppress("ReplacePrintlnWithLogging") // we're in a test context
            fun log(s: String) = println("TempDirExtension.deleteWithRetries: $s")

            path.visitFileTree {
                onVisitFile { file, attributes ->
                    try {
                        log("Removing file $file")
                        file.deleteIfExists()
                    } catch (t: Throwable) {
                        log("Removing file $file failed: ${t.stackTraceToString()}")
                        exceptions.add(t)
                    }
                    FileVisitResult.CONTINUE
                }

                onVisitFileFailed { file, exc ->
                    exc.printStackTrace()
                    exceptions.add(exc)
                    FileVisitResult.CONTINUE
                }

                onPostVisitDirectory { dir, exc ->
                    if (exc != null) {
                        exc.printStackTrace()
                        exceptions.add(exc)
                    } else {
                        try {
                            log("Removing directory $dir")
                            dir.deleteIfExists()
                        } catch (t: Throwable) {
                            log("Removing directory $dir failed: ${t.stackTraceToString()}")
                            exceptions.add(t)
                        }
                    }
                    FileVisitResult.CONTINUE
                }
            }

            if (exceptions.isEmpty()) {
                error(
                    "After a full second of retries, the second method of deleting (visitFileTree) succeeded without exceptions. " +
                            "This is very suspicious and should not generally happen. Probably a bug in visitFileTree-based deletion?" +
                            "path.exists=${path.exists()}"
                )
            } else {
                throw IllegalStateException("Got ${exceptions.size} delete exceptions for $path").also { ex ->
                    exceptions.forEach { ex.addSuppressed(it) }
                }
            }
        }
    }
}
