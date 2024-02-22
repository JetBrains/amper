/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.TraceableString


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
    fun findInCatalog(key: TraceableString): TraceableString?

    /**
     * Get dependency notation by key. Reports on a missing value.
     */
    context(ProblemReporterContext)
    fun findInCatalogWithReport(key: TraceableString): TraceableString? {
        val value = findInCatalog(key)
        if (value == null) {
            when (val trace = key.trace) {
                is PsiTrace -> {
                    SchemaBundle.reportBundleError(
                        node = trace.psiElement,
                        messageKey = "no.catalog.value",
                        key.value
                    )
                }

                else -> {}
            }
        }
        return value
    }
}