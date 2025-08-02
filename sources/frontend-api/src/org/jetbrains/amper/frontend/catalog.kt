/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.problems.reporting.ProblemReporter

/**
 * Version catalog. Currently, it supports only maven dependencies.
 */
interface VersionCatalog {

    /**
     * Get all declared catalog entry keys.
     */
    val entries: Map<String, TraceableString>

    /**
     * Whether any of the catalog entries have representation on the disk.
     */
    val isPhysical: Boolean

    /**
     * Get dependency notation by key.
     */
    fun findInCatalog(key: String): TraceableString?

    /**
     * Get dependency notation by key. Reports on a missing value.
     */
    context(problemReporter: ProblemReporter)
    fun findInCatalogWithReport(key: String, keyTrace: Trace?): TraceableString? {
        val value = findInCatalog(key)
        if (value == null && keyTrace is PsiTrace) {
            problemReporter.reportBundleError(
                source = keyTrace.psiElement.asBuildProblemSource(),
                messageKey = when {
                    key.startsWith("compose.") -> "compose.is.disabled"
                    key.startsWith("kotlin.serialization.") -> "kotlin.serialization.is.disabled"
                    else -> "no.catalog.value"
                },
                key,
            )
        }
        return value
    }
}
