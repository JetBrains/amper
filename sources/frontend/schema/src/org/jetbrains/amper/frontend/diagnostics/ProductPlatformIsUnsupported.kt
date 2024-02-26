/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.unsafe
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module

object ProductPlatformIsUnsupported : IsmDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "product.unsupported.platform"

    context(ProblemReporterContext) override fun Module.analyze() {
        product::platforms.unsafe?.forEach { platform ->
            val platformValue = platform.value
            if (platformValue !in product.type.supportedPlatforms) {
                SchemaBundle.reportBundleError(
                    value = platform,
                    messageKey = diagnosticId,
                    product.type.schemaValue,
                    platformValue.pretty,
                    product.type.supportedPlatforms.joinToString { it.pretty },
                )
            }
        }
    }
}