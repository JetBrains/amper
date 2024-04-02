/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test.extensions

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.visitFileTree

@OptIn(ExperimentalPathApi::class)
class TempDirExtension : Extension, BeforeEachCallback, AfterEachCallback {
    private val pathRef = AtomicReference<Path>(null)

    val path
        get() = pathRef.get()!!

    override fun beforeEach(context: ExtensionContext?) {
        pathRef.set(Files.createTempDirectory("test-dir"))
    }

    override fun afterEach(context: ExtensionContext?) {
        val current = pathRef.getAndSet(null)!!
        deleteWithDiagnostics(current)
    }

    companion object {
        fun deleteWithDiagnostics(path: Path) {
            val exceptions = mutableListOf<Throwable>()

            try {
                path.deleteRecursively()
                return
            } catch (t: Throwable) {
                exceptions.add(t)
            }

            fun log(s: String) = println("TempDirExtension.deleteWithRetries: $s")

            path.visitFileTree(object : FileVisitor<Path> {
                override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
                    try {
                        log("Removing file $file")
                        file.deleteIfExists()
                    } catch (t: Throwable) {
                        log("Removing file $file failed: ${t.stackTraceToString()}")
                        exceptions.add(t)
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    exc.printStackTrace()
                    exceptions.add(exc)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
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
                    return FileVisitResult.CONTINUE
                }
            })

            throw IllegalStateException("Got ${exceptions.size} delete exceptions for $path").also { ex ->
                exceptions.forEach { ex.addSuppressed(it) }
            }
        }
    }
}