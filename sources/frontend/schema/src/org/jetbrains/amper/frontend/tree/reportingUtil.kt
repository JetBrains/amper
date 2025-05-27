/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.BuildProblemImpl
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull


// FIXME Remove or move to `core` module.
interface PsiReporterCtx : ProblemReporterContext {

    /**
     * Report an error and return null, mimicking for an expected return type.
     */
    fun PsiElement.reportAndNull(
        problemId: String,
        vararg args: String?,
        messageKey: String = problemId,
        level: Level = Level.Error,
    ): Nothing? {
        val message = SchemaBundle.message(messageKey, *args)
        val source = PsiBuildProblemSource(this)
        val buildProblem = BuildProblemImpl(problemId, source, message, level)
        problemReporter.reportMessage(buildProblem)
        return null
    }
}

// FIXME Remove/rename/rethink.
interface TreeValueReporterCtx : PsiReporterCtx {

    /**
     * Report an error and return null, mimicking for an expected return type.
     */
    fun TreeValue<*>.reportAndNull(
        problemId: String,
        vararg args: String?,
        messageKey: String = problemId,
        level: Level = Level.Error,
    ): Nothing? = trace
        .extractPsiElementOrNull()
        ?.reportAndNull(problemId, *args, messageKey = messageKey, level = level)
}