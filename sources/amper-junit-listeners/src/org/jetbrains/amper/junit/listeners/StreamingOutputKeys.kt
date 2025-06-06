/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.junit.listeners

object StreamingOutputKeys {

    /**
     * Key to report output to stdout as a published entry in a streaming fashion (not in bulk at the end of the test).
     */
    const val STDOUT = "stdout-stream"

    /**
     * Key to report output to stderr as a published entry in a streaming fashion (not in bulk at the end of the test).
     */
    const val STDERR = "stderr-stream"
}
