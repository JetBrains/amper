/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.catalogs

import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.ResolvedReferenceTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.CatalogBomDependency
import org.jetbrains.amper.frontend.schema.CatalogDependency
import org.jetbrains.amper.frontend.schema.UnscopedCatalogDependency
import org.jetbrains.amper.frontend.schema.ExternalMavenBomDependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenDependency
import org.jetbrains.amper.frontend.tree.Changed
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.NotChanged
import org.jetbrains.amper.frontend.tree.Removed
import org.jetbrains.amper.frontend.tree.TransformResult
import org.jetbrains.amper.frontend.tree.TreeTransformer
import org.jetbrains.amper.frontend.tree.asScalar
import org.jetbrains.amper.frontend.tree.copy
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.problems.reporting.ProblemReporter
import kotlin.reflect.full.createType

context(buildCtx: BuildCtx)
internal fun Merged.substituteCatalogDependencies(catalog: VersionCatalog) =
    CatalogVersionsSubstitutor(catalog, buildCtx).transform(this) as? Merged ?: this

internal class CatalogVersionsSubstitutor(
    private val catalog: VersionCatalog,
    private val buildCtx: BuildCtx,
) : TreeTransformer<Merged>() {
    inline fun <reified T> getType() = buildCtx.types.getType(T::class.createType())
    private val substitutionTypes = mapOf(
        getType<CatalogDependency>() to getType<ExternalMavenDependency>(),
        getType<UnscopedCatalogDependency>() to getType<UnscopedExternalMavenDependency>(),
        getType<CatalogBomDependency>() to getType<ExternalMavenBomDependency>(),
    )

    override fun visitMapValue(value: MapLikeValue<Merged>): TransformResult<MapLikeValue<Merged>> {
        // Here we don't know what kind of node we are visiting, so we have to use `super`.
        val substituted = substitutionTypes[value.type] as? SchemaType.ObjectType ?: return super.visitMapValue(value)
        // Here we know that we have the right node (one of the dependencies), so we can return `NotChanged`.
        val catalogKeyProp = value.children.singleOrNull { it.key == "catalogKey" } ?: return NotChanged
        // TODO Maybe report here.
        val catalogKeyScalar = catalogKeyProp.value.asScalar ?: return Removed
        val catalogKey = catalogKeyScalar.value.asSafely<String>() ?: return Removed
        val found = with(buildCtx.problemReporter) {
            catalog.findInCatalogWithReport(catalogKey.removePrefix("$"), catalogKeyScalar.trace) ?: return Removed
        }
        val newCValue = catalogKeyScalar.copy(
            value = found.value,
            trace = ResolvedReferenceTrace(
                description = "from $catalogKey",
                referenceTrace = catalogKeyScalar.trace,
                resolvedValue = found,
            ),
        )
        val newChildren = value.children - catalogKeyProp +
                MapLikeValue.Property("coordinates", catalogKeyProp.kTrace, newCValue, substituted.declaration)
        return Changed(value.copy(children = newChildren, type = substituted))
    }
}

/**
 * Get dependency notation by key. Reports on a missing value.
 */
context(problemReporter: ProblemReporter)
private fun VersionCatalog.findInCatalogWithReport(key: String, keyTrace: Trace?): TraceableString? {
    val value = findInCatalog(key)
    if (value == null && keyTrace is PsiTrace) {
        problemReporter.reportBundleError(
            source = keyTrace.asBuildProblemSource(),
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