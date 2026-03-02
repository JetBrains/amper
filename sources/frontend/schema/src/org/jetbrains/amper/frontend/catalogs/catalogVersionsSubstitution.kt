/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.catalogs

import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.ResolvedReferenceTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.diagnostics.FrontendDiagnosticId
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.Changed
import org.jetbrains.amper.frontend.tree.KeyValue
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.NotChanged
import org.jetbrains.amper.frontend.tree.Removed
import org.jetbrains.amper.frontend.tree.StringNode
import org.jetbrains.amper.frontend.tree.TransformResult
import org.jetbrains.amper.frontend.tree.TreeTransformer
import org.jetbrains.amper.frontend.tree.copy
import org.jetbrains.amper.frontend.tree.declaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.problems.reporting.ProblemReporter

context(problemReporter: ProblemReporter)
internal fun MappingNode.substituteCatalogDependencies(catalog: VersionCatalog) =
    CatalogVersionsSubstitutor(catalog, problemReporter).transform(this) as? MappingNode ?: this

internal class CatalogVersionsSubstitutor(
    private val catalog: VersionCatalog,
    private val problemReporter: ProblemReporter,
) : TreeTransformer() {
    private val substitutionTypes = mapOf(
        DeclarationOfCatalogDependency to DeclarationOfExternalMavenDependency,
        DeclarationOfUnscopedCatalogDependency to DeclarationOfUnscopedExternalMavenDependency,
        DeclarationOfUnscopedCatalogBomDependency to DeclarationOfUnscopedExternalMavenBomDependency,
        DeclarationOfCatalogBomDependency to DeclarationOfExternalMavenBomDependency,
        DeclarationOfShadowDependencyCatalog to DeclarationOfShadowDependencyMaven,
    )

    override fun visitMap(node: MappingNode): TransformResult<MappingNode> {
        // Here we don't know what kind of node we are visiting, so we have to use `super`.
        val substituted = substitutionTypes[node.declaration] ?: return super.visitMap(node)
        // Here we know that we have the right node (one of the dependencies), so we can return `NotChanged`.
        val catalogKeyProp = node.children.singleOrNull { it.key == "catalogKey" } ?: return NotChanged
        // TODO Maybe report here.
        val catalogKeyScalar = catalogKeyProp.value as? StringNode ?: return Removed
        val catalogKey = catalogKeyScalar.value
        val found = context(problemReporter) {
            catalog.findInCatalogWithReport(catalogKey.removePrefix("$"), catalogKeyScalar.trace) ?: return Removed
        }
        val coordinatesProperty = checkNotNull(substituted.getProperty("coordinates")) {
            "Missing `coordinates` property in the dependency type"
        }
        val newCValue = StringNode(
            value = found.value,
            type = coordinatesProperty.type as SchemaType.StringType,
            trace = ResolvedReferenceTrace(
                description = "from $catalogKey",
                referenceTrace = catalogKeyScalar.trace,
                resolvedValue = found,
            ),
            contexts = catalogKeyScalar.contexts,
        )
        val newChildren = node.children - catalogKeyProp +
                KeyValue(catalogKeyProp.keyTrace, newCValue, coordinatesProperty, catalogKeyProp.trace)
        return Changed(node.copy(children = newChildren, type = substituted.toType()))
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
            source = keyTrace.psiElement.asBuildProblemSource(),
            diagnosticId = FrontendDiagnosticId.NoCatalogValue,
            messageKey = when {
                // TODO: This is incorrect, as Compose might be actually enabled, but the catalog reference is wrong.
                //  AMPER-5177
                key.startsWith("compose.") -> "compose.is.disabled"
                // TODO: This is incorrect, as Serialization might be actually enabled, but the catalog reference is wrong.
                //  AMPER-5177
                key.startsWith("kotlin.serialization.") -> "kotlin.serialization.is.disabled"
                else -> "no.catalog.value"
            },
            key,
        )
    }
    return value
}