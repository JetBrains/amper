/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.unsafe
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ModuleProduct

object ProductPlatformsShouldNotBeEmpty : IsmDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "product.platforms.should.not.be.empty"

    context(ProblemReporterContext) override fun Module.analyze() {
        val product = productIfDefined ?: return
        if (product::platforms.unsafe?.isEmpty() == true) {
            SchemaBundle.reportBundleError(
                property = product::platforms,
                messageKey = diagnosticId,
                level = Level.Fatal,
            )
        }
    }
}

internal val Module.productIfDefined: ModuleProduct?
    get() = ::product.unsafe