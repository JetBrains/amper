/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.catalogs

import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.withComputedValueTrace
import org.jetbrains.amper.frontend.schema.CatalogBomDependency
import org.jetbrains.amper.frontend.schema.CatalogDependency
import org.jetbrains.amper.frontend.schema.CatalogJavaAnnotationProcessorDeclaration
import org.jetbrains.amper.frontend.schema.CatalogKspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.ExternalMavenBomDependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.MavenJavaAnnotationProcessorDeclaration
import org.jetbrains.amper.frontend.schema.MavenKspProcessorDeclaration
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.TreeTransformer
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.asScalar
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.toType
import kotlin.reflect.full.createType

context(buildCtx: BuildCtx)
internal fun TreeValue<Merged>.substituteCatalogDependencies(catalog: VersionCatalog) =
    CatalogVersionsSubstitutor(catalog, buildCtx).visitValue(this)!!

internal class CatalogVersionsSubstitutor(
    private val catalog: VersionCatalog,
    private val buildCtx: BuildCtx,
) : TreeTransformer<Merged>() {
    inline fun <reified T> getType() = buildCtx.types.getType(T::class.createType())
    private val substitutionTypes = mapOf(
        getType<CatalogDependency>() to getType<ExternalMavenDependency>(),
        getType<CatalogKspProcessorDeclaration>() to getType<MavenKspProcessorDeclaration>(),
        getType<CatalogJavaAnnotationProcessorDeclaration>() to getType<MavenJavaAnnotationProcessorDeclaration>(),
        getType<CatalogBomDependency>() to getType<ExternalMavenBomDependency>(),
    )

    override fun visitMapValue(value: MapLikeValue<Merged>): MergedTree? {
        val valueType = value.type?.toType() ?: return super.visitMapValue(value)
        val substituted = substitutionTypes[valueType] as? SchemaType.ObjectType ?: return super.visitMapValue(value)
        val catalogKeyProp = value.children.singleOrNull { it.key == "catalogKey" } ?: return super.visitMapValue(value)
        // TODO Maybe report here.
        val catalogValue = catalogKeyProp.value.asScalar ?: return null
        val catalogKey = catalogValue.value.asSafely<String>() ?: return null
        val found = with(buildCtx.problemReporter) {
            catalog.findInCatalogWithReport(catalogKey.removePrefix("$"), catalogValue.trace) ?: return null
        }
        val newCValue = catalogValue.copy(
            value = found.value, 
            trace = catalogValue.trace.withComputedValueTrace(found),
        )
        val newChildren = value.children - catalogKeyProp + 
                MapLikeValue.Property("coordinates", catalogKeyProp.kTrace, newCValue, substituted.declaration)
        return value.copy(children = newChildren, type = substituted.declaration)
    }
}
