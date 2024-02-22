/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.messages

import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.FileLocatedBuildProblemSource
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
sealed interface PsiBuildProblemSource : FileLocatedBuildProblemSource {
    @UsedInIdePlugin
    val psiElement: PsiElement

    class FileSystemLike internal constructor(override val psiElement: PsiFileSystemItem) : PsiBuildProblemSource {
        override val file: Path
            get() = psiElement.virtualFile?.toNioPathOrNull() ?: error(NO_VIRTUAL_FILE_ERROR)

        override val range: LineAndColumnRange? = null

        override val offsetRange: IntRange? = null
    }

    class Element internal constructor(override val psiElement: PsiElement) : PsiBuildProblemSource {
        override val file: Path
            get() = psiElement.containingFile?.originalFile?.virtualFile?.toNioPathOrNull()
                ?: error(NO_VIRTUAL_FILE_ERROR)

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