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

class PsiBuildProblemSource(@UsedInIdePlugin val psiElement: PsiElement) : FileLocatedBuildProblemSource {
    override val file: Path
        get() = if (psiElement is PsiFileSystemItem) {
            psiElement.virtualFile
        } else {
            psiElement.containingFile?.originalFile?.virtualFile
        }?.toNioPathOrNull() ?: error("PSI element doesn't have real backing file")

    override val range: LineAndColumnRange?
        get() = if (psiElement is PsiFileSystemItem) {
            null
        } else {
            getLineAndColumnRangeInPsiFile(psiElement)
        }

    override val offsetRange: IntRange?
        get() = if (psiElement is PsiFileSystemItem) {
            null
        } else {
            val range = psiElement.textRange
            IntRange(range.startOffset, range.endOffset)
        }
}