/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.extract

import java.io.FilterInputStream
import java.io.InputStream

/**
 * A stream that prevents the underlying input stream from being closed.
 *
 * This class is typically used in cases where an input stream needs to be passed to a component that wants to
 * explicitly close the stream even if more input would still be available to other components.
 */
internal class CloseShieldInputStream(inputStream: InputStream?) : FilterInputStream(inputStream) {
    /**
     * Replaces the underlying input stream with a [ClosedInputStream] sentinel.
     * The original input stream will remain open, but this proxy will appear closed.
     */
    override fun close() {
        `in` = ClosedInputStream
    }
}

/**
 * Always returns EOF to all attempts to read something from the stream.
 */
private object ClosedInputStream : InputStream() {
    override fun read(): Int = -1
}
