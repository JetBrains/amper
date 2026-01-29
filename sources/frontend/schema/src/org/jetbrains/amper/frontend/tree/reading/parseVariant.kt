/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.plugins.TaskAction
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
import org.jetbrains.amper.frontend.schema.UnscopedBomDependency
import org.jetbrains.amper.frontend.schema.UnscopedCatalogBomDependency
import org.jetbrains.amper.frontend.schema.UnscopedCatalogDependency
import org.jetbrains.amper.frontend.schema.UnscopedDependency
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenBomDependency
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenDependency
import org.jetbrains.amper.frontend.schema.UnscopedModuleDependency
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.tree.copyWithTrace
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.SchemaVariantDeclaration
import org.jetbrains.amper.problems.reporting.ProblemReporter
import kotlin.reflect.KClass

context(_: Contexts, _: ParsingConfig, reporter: ProblemReporter)
internal fun parseVariant(
    value: YamlValue,
    type: SchemaType.VariantType,
): TreeNode? = when (type.declaration.qualifiedName) {
    Dependency::class.qualifiedName!! -> {
        val singleKeyValue = (value as? YamlValue.Mapping)?.keyValues?.singleOrNull()
        if (singleKeyValue != null && singleKeyValue.key.psi.text == "bom") {
            parseNode(singleKeyValue.value, type.subVariantType(BomDependency::class))
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
    UnscopedDependency::class.qualifiedName -> {
        val singleKeyValue = (value as? YamlValue.Mapping)?.keyValues?.singleOrNull()
        if (singleKeyValue != null && singleKeyValue.key.psi.text == "bom") {
            singleKeyValue.value.let { bomDependency ->
                parseVariant(bomDependency, type.subVariantType(UnscopedBomDependency::class))
                    ?.copyWithTrace(trace = singleKeyValue.asTrace())
            } ?: run {
                reportParsing(singleKeyValue.value, "unexpected.bom.dependency.structure")
                null
            }
        } else {
            when (peekValueAsKey(value)?.firstOrNull()) {
                '.' -> parseObject(value, type.leafType(UnscopedModuleDependency::class))
                '$' -> parseObject(value, type.leafType(UnscopedCatalogDependency::class))
                else -> parseObject(value, type.leafType(UnscopedExternalMavenDependency::class))
            }
        }
    }
    UnscopedBomDependency::class.qualifiedName -> when (peekValueAsKey(value)?.firstOrNull()) {
        '.' -> {
            reportParsing(value, "unexpected.bom.local",)
            null
        }
        '$' -> parseObject(value, type.leafType(UnscopedCatalogBomDependency::class))
        else -> parseObject(value, type.leafType(UnscopedExternalMavenBomDependency::class))
    }
    ShadowDependency::class.qualifiedName -> when (peekValueAsKey(value)?.firstOrNull()) {
        '.' -> parseObject(value, type.leafType(ShadowDependencyLocal::class))
        '$' -> parseObject(value, type.leafType(ShadowDependencyCatalog::class))
        else -> parseObject(value, type.leafType(ShadowDependencyMaven::class))
    }
    TaskAction::class.qualifiedName -> {
        val tag = value.tag
        if (tag == null) {
            reporter.reportMessage(MissingTaskActionType(element = value.psi, taskActionType = type.declaration))
            return null
        }
        val requestedTypeName = tag.text.removePrefix("!")
        val variant = type.declaration.variants.find { it.qualifiedName == requestedTypeName }
        if (variant == null) {
            reporter.reportMessage(
                InvalidTaskActionType(
                    element = tag,
                    invalidType = requestedTypeName,
                    taskActionType = type.declaration,
                )
            )
            null
        } else {
            parseObject(value, variant.toType(), allowTypeTag = true)
        }
    }
    else -> {
        // NOTE: When (if) we support user-defined sealed classes based on type tags,
        // replace the error with a meaningful description
        error("Unhandled variant type: ${type.declaration.qualifiedName}")
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
