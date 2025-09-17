/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.messages

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.getLineAndColumnRangeInPsiFile
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.FileWithRangesBuildProblemSource
import org.jetbrains.amper.problems.reporting.LineAndColumnRange
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
    @Deprecated(
        "Use psiElementPointer instead, as it doesn't keep long-living references to the PSI",
        replaceWith = ReplaceWith("psiPointer.element ?: error(\"No PSI element found\")")
    )
    val psiElement: PsiElement
        get() = psiPointer.element ?: error(NO_VIRTUAL_FILE_ERROR)

    @UsedInIdePlugin
    val psiPointer: SmartPsiElementPointer<out PsiElement>

    override val file: Path
        get() = psiPointer.originalFilePath ?: error(NO_VIRTUAL_FILE_ERROR)

    data class FileSystemLike internal constructor(override val psiPointer: SmartPsiElementPointer<PsiFileSystemItem>) : PsiBuildProblemSource

    data class Element internal constructor(override val psiPointer: SmartPsiElementPointer<out PsiElement>) : PsiBuildProblemSource, FileWithRangesBuildProblemSource {
        override val range: LineAndColumnRange?
            get() {
                val psiElement = psiPointer.element ?: return null
                return getLineAndColumnRangeInPsiFile(psiElement)
            }

        override val offsetRange: IntRange?
            get() {
                val psiElement = psiPointer.element ?: return null
                val range = psiElement.textRange
                return IntRange(range.startOffset, range.endOffset)
            }
    }
}

fun PsiBuildProblemSource(psiPointer: SmartPsiElementPointer<out PsiElement>): PsiBuildProblemSource =
    if (psiPointer.element is PsiFileSystemItem) {
        @Suppress("UNCHECKED_CAST")
        PsiBuildProblemSource.FileSystemLike(psiPointer as SmartPsiElementPointer<PsiFileSystemItem>)
    } else {
        PsiBuildProblemSource.Element(psiPointer)
    }