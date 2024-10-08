/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import org.jetbrains.amper.core.messages.BuildProblemImpl
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource

context(Converter)
internal fun reportError(problemId: String,
                        source: AmperElementWrapper?,
                        messageKey: String = problemId,
                        vararg args: String?) {
    source ?: return
    problemReporter.reportMessage(
        BuildProblemImpl(problemId,
            PsiBuildProblemSource(source.sourceElement),
            SchemaBundle.message(messageKey, *args),
            Level.Error
        )
    )
}

context(Converter)
internal fun reportWarning(problemId: String,
                          source: AmperElementWrapper?,
                          messageKey: String = problemId,
                          vararg args: String?) {
    source ?: return
    problemReporter.reportMessage(
        BuildProblemImpl(problemId,
            PsiBuildProblemSource(source.sourceElement),
            SchemaBundle.message(messageKey, *args),
            Level.Warning
        )
    )
}