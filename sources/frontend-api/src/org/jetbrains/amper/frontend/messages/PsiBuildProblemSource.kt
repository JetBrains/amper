/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.messages

import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.FileBuildProblemSource
import org.jetbrains.amper.core.messages.FileWithRangesBuildProblemSource
import org.jetbrains.amper.core.messages.LineAndColumnRange
import org.jetbrains.amper.frontend.getLineAndColumnRangeInPsiFile
import java.nio.file.Path

private const val NO_VIRTUAL_FILE_ERROR = "PSI element doesn't have real backing file"

/**
 * Location in the file designated by the node of a PSI tree.
 * Is useful for IntelliJ integration as most of its features are working over PSI, so instead of determining
 * the element at the offset, the IDE can just retrieve it as a field from the source.
 *
 * Use [PsiBuildProblemSource] factory function to construct.
 */
sealed interface PsiBuildProblemSource : FileBuildProblemSource {
    @UsedInIdePlugin
    val psiElement: PsiElement

    override val file: Path
        get() = psiElement.originalFilePath ?: error(NO_VIRTUAL_FILE_ERROR)

    data class FileSystemLike internal constructor(override val psiElement: PsiFileSystemItem) : PsiBuildProblemSource

    data class Element internal constructor(override val psiElement: PsiElement) : PsiBuildProblemSource, FileWithRangesBuildProblemSource {
        override val range: LineAndColumnRange
            get() = getLineAndColumnRangeInPsiFile(psiElement)

        override val offsetRange: IntRange
            get() {
                val range = psiElement.textRange
                return IntRange(range.startOffset, range.endOffset)
            }
    }
}

fun PsiBuildProblemSource(psiElement: PsiElement): PsiBuildProblemSource =
    if (psiElement is PsiFileSystemItem) {
        PsiBuildProblemSource.FileSystemLike(psiElement)
    } else {
        PsiBuildProblemSource.Element(psiElement)
    }