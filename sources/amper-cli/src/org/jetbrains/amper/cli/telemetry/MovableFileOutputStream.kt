/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.telemetry

import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.outputStream

/**
 * An output stream to a file that can be moved concurrently with the writes.
 *
 * The stream initially writes to [initialPath], and then [moveTo] can be used to change the path.
 */
internal class MovableFileOutputStream(initialPath: Path) : OutputStream() {

    private var currentPath = initialPath
    private var fileStream = initialPath.outputStream().buffered()

    /**
     * Moves the destination file of this [MovableFileOutputStream] to the given [newPath].
     * This is not just a path change: when this method is called, the write operations are temporarily blocked while
     * the file is being physically moved to the new location.
     * The subsequent write operations will append to the file in the new location.
     */
    @Synchronized
    fun moveTo(newPath: Path) {
        if (newPath == currentPath) {
            return
        }
        try {
            fileStream.flush()
        } finally {
            fileStream.close()
        }
        // if nothing has been written so far, the file might not exist at all
        if (currentPath.exists()) {
            currentPath.moveTo(newPath)
        }
        currentPath = newPath
        fileStream = newPath.outputStream(
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        ).buffered()
    }

    @Synchronized
    override fun write(b: Int) {
        fileStream.write(b)
    }

    @Synchronized
    override fun write(b: ByteArray) {
        fileStream.write(b)
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        fileStream.write(b, off, len)
    }

    @Synchronized
    override fun flush() {
        fileStream.flush()
    }

    @Synchronized
    override fun close() {
        fileStream.close()
    }
}