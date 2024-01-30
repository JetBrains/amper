/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.yaml.psi.YAMLPsiElement


/**
 * Version catalog. Currently, supports only maven dependencies.
 */
interface VersionCatalog {

    /**
     * Get all declared catalog entry keys.
     */
    val entries: Map<String, TraceableString>

    /**
     * Get dependency notation by key.
     */
    context(ProblemReporterContext)
    fun findInCatalog(
        key: TraceableString,
        report: Boolean = true,
    ): TraceableString?

    context(ProblemReporterContext)
    fun tryReportCatalogKeyAbsence(key: TraceableString, needReport: Boolean): Nothing? =
        if (needReport) {
            when (val trace = key.trace) {
                is YAMLPsiElement -> {
                    SchemaBundle.reportBundleError(
                        trace,
                        "no.catalog.value",
                        key.value
                    )
                }
            }
            null
        } else null

}