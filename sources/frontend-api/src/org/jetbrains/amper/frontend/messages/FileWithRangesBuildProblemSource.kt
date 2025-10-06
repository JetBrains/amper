/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.messages

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.findDocument
import org.jetbrains.amper.frontend.getLineAndColumnRangeInDocument
import org.jetbrains.amper.problems.reporting.FileWithRangesBuildProblemSource
import org.jetbrains.amper.problems.reporting.LineAndColumn
import org.jetbrains.amper.problems.reporting.LineAndColumnRange
import java.nio.file.Path

fun FileWithRangesBuildProblemSource(
    file: Path,
    offsetRange: IntRange,
): FileWithRangesBuildProblemSource = object : FileWithRangesBuildProblemSource {
    override val offsetRange: IntRange get() = offsetRange
    override val file: Path get() = file
    override val range: LineAndColumnRange
        get() = runReadAction {
            VirtualFileManager.getInstance().findFileByNioPath(file)
        }?.findDocument()?.let {
            getLineAndColumnRangeInDocument(it, offsetRange)
        } ?: LineAndColumnRange(LineAndColumn.NONE, LineAndColumn.NONE)
}
