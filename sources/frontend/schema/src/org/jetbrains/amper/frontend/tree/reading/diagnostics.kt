/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.types.SchemaVariantDeclaration
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.annotations.Nls

class InvalidTaskActionType(
    override val element: PsiElement,
    val invalidType: String,
    val taskActionType: SchemaVariantDeclaration,
) : PsiBuildProblem(
    level = Level.Error,
    type = BuildProblemType.UnknownSymbol,
) {
    companion object {
        const val ID = "validation.types.tag.task.action.invalid"
    }

    override val buildProblemId get() = ID

    override val message: @Nls String by lazy {
        SchemaBundle.message(ID, invalidType, formatAvailableTasks(taskActionType))
    }
}

class MissingTaskActionType(
    override val element: PsiElement,
    val taskActionType: SchemaVariantDeclaration,
) : PsiBuildProblem(
    level = Level.Error,
    type = BuildProblemType.Generic,
) {
    companion object {
        const val ID = "validation.types.tag.task.action.missing"
    }

    override val buildProblemId get() = ID

    override val message: @Nls String by lazy {
        SchemaBundle.message(ID, formatAvailableTasks(taskActionType))
    }
}

private fun formatAvailableTasks(type: SchemaVariantDeclaration): String {
    val string = if (type.variants.isEmpty())
        "<none>" else type.variants.joinToString { it.displayName }
    return string
}