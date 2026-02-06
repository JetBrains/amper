/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.telemetry

import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.outputStream

/**
 * An output stream to a file that can be moved concurrently with the writes.
 *
 * The stream initially writes to [initialPath], and then [moveTo] can be used to change the path.
 *
 * This implementation is thread-safe. It is safe to concurrently [write], [flush], or [close] this stream, or even move
 * the underlying file with [moveTo]. Writes will always go to the correct stream.
 *
 * Like [Path.outputStream], this stream implementation is not buffered. It's the responsibility of the caller
 * to wrap this stream in a buffered stream if needed.
 *
 * Whether the returned stream is *asynchronously closeable* and/or *interruptible* is highly file system provider
 * specific and therefore not specified.
 */
internal class MovableFileOutputStream(initialPath: Path) : OutputStream() {

    private var currentPath = initialPath
    private var fileStream = initialPath.outputStream()

    /**
     * A lock used when moving the underlying file with [moveTo]. This ensures no stream operations are done while we
     * are moving the file, and that we're not moving the file to different locations concurrently.
     *
     * Note: we don't need to synchronize operations on [fileStream] between each other, we just need to prevent them
     * from happening concurrently with [moveTo] operations, hence the read/write lock approach.
     */
    private val moveLock = ReentrantReadWriteLock()

    /**
     * Moves the destination file of this [MovableFileOutputStream] to the given [newPath].
     *
     * This is not just a path change to start writing to a new file: when this method is called, the write operations
     * are temporarily blocked while the file is being physically moved to the new location.
     * The subsequent write operations will append to the file in the new location.
     */
    fun moveTo(newPath: Path) {
        if (newPath == currentPath) {
            return
        }
        moveLock.write {
            if (newPath == currentPath) {
                return
            }
            fileStream.close() // takes care of flushing as well

            // if nothing has been written so far, the file might not exist at all, thus no need to move it in that case
            if (currentPath.exists()) {
                currentPath.moveTo(newPath)
            }
            currentPath = newPath
            fileStream = newPath.outputStream(
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE, // in case nothing had been written yet (the file was never created so far)
                StandardOpenOption.APPEND, // we want to append to the existing (moved) file, so not TRUNCATE_EXISTING
            )
        }
    }

    override fun write(b: Int) {
        moveLock.read {
            fileStream.write(b)
        }
    }

    override fun write(b: ByteArray) {
        moveLock.read {
            fileStream.write(b)
        }
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        moveLock.read {
            fileStream.write(b, off, len)
        }
    }

    override fun flush() {
        moveLock.read {
            fileStream.flush()
        }
    }

    override fun close() {
        moveLock.read {
            fileStream.close()
        }
    }
}