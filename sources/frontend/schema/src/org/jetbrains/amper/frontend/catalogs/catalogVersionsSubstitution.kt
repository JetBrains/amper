/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.catalogs

import com.intellij.util.asSafely
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.withComputedValueTrace
import org.jetbrains.amper.frontend.schema.CatalogBomDependency
import org.jetbrains.amper.frontend.types.ATypes
import org.jetbrains.amper.frontend.schema.CatalogDependency
import org.jetbrains.amper.frontend.schema.CatalogJavaAnnotationProcessorDeclaration
import org.jetbrains.amper.frontend.schema.CatalogKspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.ExternalMavenBomDependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.MavenJavaAnnotationProcessorDeclaration
import org.jetbrains.amper.frontend.schema.MavenKspProcessorDeclaration
import org.jetbrains.amper.frontend.tree.TreeTransformer
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.asScalar
import kotlin.reflect.full.starProjectedType


context(BuildCtx)
internal fun TreeValue<Merged>.substituteCatalogDependencies(catalog: VersionCatalog) =
    CatalogVersionsSubstitutor(catalog, this@BuildCtx).visitValue(this)!!

internal class CatalogVersionsSubstitutor(
    private val catalog: VersionCatalog,
    private val buildCtx: BuildCtx,
) : TreeTransformer<Merged>(), ProblemReporterContext by buildCtx {
    inline fun <reified T> aType() = buildCtx.types[T::class.starProjectedType]
    private val substitutionTypes = mapOf(
        aType<CatalogDependency>() to aType<ExternalMavenDependency>(),
        aType<CatalogKspProcessorDeclaration>() to aType<MavenKspProcessorDeclaration>(),
        aType<CatalogJavaAnnotationProcessorDeclaration>() to aType<MavenJavaAnnotationProcessorDeclaration>(),
        aType<CatalogBomDependency>() to aType<ExternalMavenBomDependency>(),
    )

    override fun visitMapValue(value: MapLikeValue<Merged>): MergedTree? {
        val valueType = value.type ?: return super.visitMapValue(value)
        val substituted = substitutionTypes[valueType].asSafely<ATypes.AObject>() ?: return super.visitMapValue(value)
        val catalogKeyProp = value.children.singleOrNull { it.key == "catalogKey" } ?: return super.visitMapValue(value)
        // TODO Maybe report here.
        val catalogValue = catalogKeyProp.value.asScalar ?: return null
        val catalogKey = catalogValue.value.asSafely<String>() ?: return null
        val found = catalog.findInCatalogWithReport(catalogKey.removePrefix("$"), catalogValue.trace) ?: return null
        val newCValue = catalogValue.copy(
            value = found.value, 
            trace = catalogValue.trace.withComputedValueTrace(found),
        )
        val newChildren = value.children - catalogKeyProp + 
                MapLikeValue.Property("coordinates", catalogKeyProp.kTrace, newCValue, substituted)
        return value.copy(children = newChildren, type = substituted)
    }
}