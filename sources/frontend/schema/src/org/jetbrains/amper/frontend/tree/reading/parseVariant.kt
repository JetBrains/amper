/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

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
import kotlin.reflect.KClass

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseVariant(
    value: YamlValue,
    type: SchemaType.VariantType,
): TreeValue<*>? = when (type.declaration.qualifiedName) {
    Dependency::class.qualifiedName!! -> {
        val singleKeyValue = (value as? YamlValue.Mapping)?.keyValues?.singleOrNull()
        if (singleKeyValue != null && singleKeyValue.key.psi.text == "bom") {
            parseValue(singleKeyValue.value, type.subVariantType(BomDependency::class))
                ?.copyWithTrace(trace = singleKeyValue.asTrace()) ?: run {
                reportParsing(singleKeyValue.value, "unexpected.bom.dependency.structure")
                null
            }
        } else {
            parseVariant(value, type.subVariantType(ScopedDependency::class))
        }
    }
    BomDependency::class.qualifiedName -> when (peekValueAsKey(value)?.firstOrNull()) {
        '.' -> {
            reportParsing(value, "unexpected.bom.local",)
            null
        }
        '$' -> parseObject(value, type.leafType(CatalogBomDependency::class))
        else -> parseObject(value, type.leafType(ExternalMavenBomDependency::class))
    }
    ScopedDependency::class.qualifiedName -> when (peekValueAsKey(value)?.firstOrNull()) {
        '.' -> parseObject(value, type.leafType(InternalDependency::class))
        '$' -> parseObject(value, type.leafType(CatalogDependency::class))
        else -> parseObject(value, type.leafType(ExternalMavenDependency::class))
    }
    UnscopedDependency::class.qualifiedName -> when (peekValueAsKey(value)?.firstOrNull()) {
        '.' -> parseObject(value, type.leafType(UnscopedModuleDependency::class))
        '$' -> parseObject(value, type.leafType(UnscopedCatalogDependency::class))
        else -> parseObject(value, type.leafType(UnscopedExternalMavenDependency::class))
    }
    ShadowDependency::class.qualifiedName -> when (peekValueAsKey(value)?.firstOrNull()) {
        '.' -> parseObject(value, type.leafType(ShadowDependencyLocal::class))
        '$' -> parseObject(value, type.leafType(ShadowDependencyCatalog::class))
        else -> parseObject(value, type.leafType(ShadowDependencyMaven::class))
    }
    else -> {
        // Generic approach: deduce the type based on the explicit type tag.
        val possibleTagsString by lazy { type.declaration.variants.joinToString { '!' + it.qualifiedName } }
        when(val tag = value.tag) {
            null -> {
                reportParsing(value, "validation.types.missing.tag", possibleTagsString)
                null
            }
            else -> {
                val requestedName = tag.text.removePrefix("!")
                when(val variant = type.declaration.variants.find { it.qualifiedName == requestedName }) {
                    null -> {
                        reportParsing(tag, "validation.types.unknown.tag", possibleTagsString)
                        null
                    }
                    else -> parseObject(value, variant.toType(), allowTypeTag = true)
                }
            }
        }
    }
}

private fun peekValueAsKey(psi: YamlValue): String? = when (psi) {
    is YamlValue.Mapping -> psi.keyValues.singleOrNull()?.key?.psi?.text
    is YamlValue.Scalar -> psi.textValue
    else -> null
}

private fun SchemaType.VariantType.leafType(kClass: KClass<*>): SchemaType.ObjectType =
    declaration.variantTree.first { it.declaration.qualifiedName == kClass.qualifiedName }
        .let { it as SchemaVariantDeclaration.Variant.LeafVariant }.declaration.toType()

private fun SchemaType.VariantType.subVariantType(kClass: KClass<*>): SchemaType.VariantType =
    declaration.variantTree.first { it.declaration.qualifiedName == kClass.qualifiedName }
        .let { it as SchemaVariantDeclaration.Variant.SubVariant }.declaration.toType()
