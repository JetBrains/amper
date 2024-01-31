/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.withoutDefault
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.commonSettings


/**
 * Perform additional validations on the whole model.
 */
context(ProblemReporterContext)
fun Model.performValidations() = apply {
    checkComposeVersionsConsistency()
}

/**
 * Check that compose versions are the same across all modules.
 */
context(ProblemReporterContext)
fun Model.checkComposeVersionsConsistency() {
    val chosenComposeVersionForModel = chooseComposeVersion(this) ?: return

    val setUpComposeSettings = modules.map { it.origin.commonSettings.compose }
        .filter { it.version != null }

    val groupedByComposeVersions = setUpComposeSettings
        .groupBy { it.version }

    if (groupedByComposeVersions.size > 1) {
        setUpComposeSettings.forEach {
            // If the user set up version.
            if (it::version.withoutDefault != null && it.version != chosenComposeVersionForModel) {
                SchemaBundle.reportBundleError(
                    it::version,
                    "inconsistent.compose.versions",
                    chosenComposeVersionForModel,
                    level = Level.Fatal,
                )
            }

            // If the default is used.
            if (it::version.withoutDefault == null && it.version != chosenComposeVersionForModel) {
                SchemaBundle.reportBundleError(
                    it::enabled,
                    "inconsistent.compose.versions",
                    chosenComposeVersionForModel,
                    level = Level.Fatal,
                )
            }
        }
    }
}

/**
 * Try to find single compose version within model.
 */
fun chooseComposeVersion(model: Model) = model.modules
    .map { it.origin.commonSettings.compose }
    .filter { it.enabled }
    .mapNotNull { it.version }
    .maxWithOrNull(compareBy { ComparableVersion(it) })
