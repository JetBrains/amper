/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.plugins.generated.ShadowDependency
import org.jetbrains.amper.frontend.plugins.generated.ShadowDependencyCatalog
import org.jetbrains.amper.frontend.plugins.generated.ShadowDependencyLocal
import org.jetbrains.amper.frontend.plugins.generated.ShadowDependencyMaven
import org.jetbrains.amper.frontend.schema.BomDependency
import org.jetbrains.amper.frontend.schema.CatalogBomDependency
import org.jetbrains.amper.frontend.schema.CatalogDependency
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.ExternalMavenBomDependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.InternalDependency
import org.jetbrains.amper.frontend.schema.ScopedDependency
import org.jetbrains.amper.frontend.schema.UnscopedCatalogDependency
import org.jetbrains.amper.frontend.schema.UnscopedDependency
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenDependency
import org.jetbrains.amper.frontend.schema.UnscopedModuleDependency
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.SchemaVariantDeclaration
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLValue
import kotlin.reflect.KClass

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseVariant(
    psi: YAMLValue,
    type: SchemaType.VariantType,
): TreeValue<*>? = when (type.declaration.qualifiedName) {
    Dependency::class.qualifiedName!! -> {
        val singleKeyValue = (psi as? YAMLMapping)?.keyValues?.singleOrNull()
        if (singleKeyValue != null && singleKeyValue.keyText == "bom") {
            singleKeyValue.value?.let { bomDependency ->
                parseVariant(bomDependency, type.subVariantType(BomDependency::class))
                    ?.copyWithTrace(trace = singleKeyValue.asTrace())
            } ?: run {
                reportParsing(singleKeyValue, "unexpected.bom.dependency.structure")
                null
            }
        } else {
            parseVariant(psi, type.subVariantType(ScopedDependency::class))
        }
    }
    BomDependency::class.qualifiedName -> when (peekValueAsKey(psi)?.firstOrNull()) {
        '.' -> {
            reportParsing(psi, "unexpected.bom.local",)
            null
        }
        '$' -> parseObject(psi, type.leafType(CatalogBomDependency::class))
        else -> parseObject(psi, type.leafType(ExternalMavenBomDependency::class))
    }
    ScopedDependency::class.qualifiedName -> when (peekValueAsKey(psi)?.firstOrNull()) {
        '.' -> parseObject(psi, type.leafType(InternalDependency::class))
        '$' -> parseObject(psi, type.leafType(CatalogDependency::class))
        else -> parseObject(psi, type.leafType(ExternalMavenDependency::class))
    }
    UnscopedDependency::class.qualifiedName -> when (peekValueAsKey(psi)?.firstOrNull()) {
        '.' -> parseObject(psi, type.leafType(UnscopedModuleDependency::class))
        '$' -> parseObject(psi, type.leafType(UnscopedCatalogDependency::class))
        else -> parseObject(psi, type.leafType(UnscopedExternalMavenDependency::class))
    }
    ShadowDependency::class.qualifiedName -> when (peekValueAsKey(psi)?.firstOrNull()) {
        '.' -> parseObject(psi, type.leafType(ShadowDependencyLocal::class))
        '$' -> parseObject(psi, type.leafType(ShadowDependencyCatalog::class))
        else -> parseObject(psi, type.leafType(ShadowDependencyMaven::class))
    }
    else -> {
        // Generic approach: deduce the type based on the explicit type tag.
        val possibleTagsString by lazy { type.declaration.variants.joinToString { '!' + it.qualifiedName } }
        when(val tag = psi.tag) {
            null -> {
                reportParsing(psi, "validation.types.missing.tag", possibleTagsString)
                null
            }
            else -> {
                val requestedName = tag.text.removePrefix("!")
                when(val variant = type.declaration.variants.find { it.qualifiedName == requestedName }) {
                    null -> {
                        reportParsing(tag, "validation.types.unknown.tag", possibleTagsString)
                        null
                    }
                    else -> parseObject(psi, variant.toType(), allowTypeTag = true)
                }
            }
        }
    }
}

private fun peekValueAsKey(psi: YAMLValue): String? = when (psi) {
    is YAMLMapping -> psi.keyValues.singleOrNull()?.keyText
    is YAMLScalar -> psi.textValue
    else -> null
}

private fun SchemaType.VariantType.leafType(kClass: KClass<*>): SchemaType.ObjectType =
    declaration.variantTree.first { it.declaration.qualifiedName == kClass.qualifiedName }
        .let { it as SchemaVariantDeclaration.Variant.LeafVariant }.declaration.toType()

private fun SchemaType.VariantType.subVariantType(kClass: KClass<*>): SchemaType.VariantType =
    declaration.variantTree.first { it.declaration.qualifiedName == kClass.qualifiedName }
        .let { it as SchemaVariantDeclaration.Variant.SubVariant }.declaration.toType()
