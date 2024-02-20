/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.messages

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.BuildProblemSource
import org.jetbrains.amper.core.messages.LineAndColumnRange
import org.jetbrains.amper.frontend.getLineAndColumnRangeInPsiFile
import java.nio.file.Path

class PsiBuildProblemSource(@UsedInIdePlugin val psiElement: PsiElement) : BuildProblemSource {
    override val file: Path? = run {
        val virtualFile: VirtualFile? = if (psiElement is PsiFileSystemItem) {
            psiElement.virtualFile
        } else {
            psiElement.containingFile?.originalFile?.virtualFile
        }
        virtualFile?.toNioPathOrNull()
    }

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