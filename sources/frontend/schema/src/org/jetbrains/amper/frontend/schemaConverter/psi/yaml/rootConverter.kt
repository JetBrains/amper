/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.yaml

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.asTraceable
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.AmperLayout
import org.jetbrains.amper.frontend.schema.Base
import org.jetbrains.amper.frontend.schema.CatalogDependency
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.DependencyScope
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.InternalDependency
import org.jetbrains.amper.frontend.schema.Meta
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.Repository
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.psi.assertNodeType
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem
import org.jetbrains.yaml.psi.YAMLValue

context(ProblemReporterContext, ConvertCtx)
fun YAMLDocument.convertTemplate() = Template().apply {
    val documentMapping = getTopLevelValue()?.asMappingNode() ?: return@apply
    with(documentMapping) {
        convertBase(this@apply)
    }
}

context(ProblemReporterContext, ConvertCtx)
fun YAMLDocument.convertModule() = Module().apply {
    val documentMapping = getTopLevelValue()?.asMappingNode() ?: return@apply
    with(documentMapping) {
        ::product.convertChild { convertProduct() }
        this@apply::apply.convertChildScalarCollection { asAbsolutePath() }
        ::aliases.convertChild {
            asSequenceNode()?.convertScalarKeyedMap {
                asSequenceNode()
                    ?.asScalarSequenceNode()
                    ?.mapNotNull { it.convertEnum(Platform)?.asTraceable()?.adjustTrace(this) }
                    ?.toSet()
            }
        }
        ::module.convertChild { asMappingNode()?.convertMeta() }
        convertBase(this@apply)
    }
}

context(ProblemReporterContext, ConvertCtx)
internal fun <T : Base> YAMLMapping.convertBase(base: T) = base.apply {
    ::repositories.convertChild { convertRepositories() }

    ::dependencies.convertModifierAware { value?.convertDependencies() }
    ::`test-dependencies`.convertModifierAware { value?.convertDependencies() }

    ::settings.convertModifierAware(Settings()) { convertSettings() }
    ::`test-settings`.convertModifierAware(Settings()) { convertSettings() }
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLKeyValue.convertProduct() = ModuleProduct().apply {
    when (val productNodeValue = this@convertProduct.value) {
        is YAMLMapping -> with(productNodeValue) {
            ::type.convertChildEnum(ProductType, isFatal = true, isLong = true)
            ::platforms.convertChildScalarCollection { convertEnum(Platform)?.asTraceable()?.adjustTrace(this) }
        }
        is YAMLScalar -> ::type.convertSelf { productNodeValue.convertEnum(ProductType, isFatal = true, isLong = true) }
        else -> productNodeValue?.let {
            SchemaBundle.reportBundleError(
                node = productNodeValue,
                messageKey = "unexpected.product.node.type",
                productNodeValue::class.simpleName,
            )
        }
    }
}

context(ConvertCtx, ProblemReporterContext)
private fun YAMLMapping.convertMeta() = Meta().apply {
    ::layout.convertChildEnum(AmperLayout)
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLKeyValue.convertRepositories(): List<Repository>? {
    (this.value as? YAMLSequence)?.let {
        return it.items.mapNotNull { it.convertRepository() }
    }
    // TODO Report wrong type.
    return null
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLSequenceItem.convertRepository(): Repository? = when (val value = value) {
    is YAMLScalar -> value.convertRepositoryShort()
    is YAMLMapping -> value.convertRepositoryFull()
    else -> null
}?.adjustTrace(this.value)

context(ProblemReporterContext, ConvertCtx)
private fun YAMLScalar.convertRepositoryShort() = Repository().apply {
    ::url.convertSelf { textValue }
    ::id.convertSelf { textValue }
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLMapping.convertRepositoryFull(): Repository = Repository().apply {
    ::url.convertChildString()
    ::id.convertChildString()
    ::publish.convertChildBoolean()
    ::credentials.convertChild {
        asMappingNode()?.run {
            Repository.Credentials().apply {
                // TODO Report non existent path.
                ::file.convertChildScalar { asAbsolutePath() }
                ::usernameKey.convertChildString()
                ::passwordKey.convertChildString()
            }
        }
    }
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLValue.convertDependencies() =
    assertNodeType<YAMLSequence, List<Dependency>>("dependencies") {
        items.mapNotNull { it.convertDependency() }
    }

context(ProblemReporterContext, ConvertCtx)
private fun YAMLSequenceItem.convertDependency(): Dependency? = when (val value = value) {
    is YAMLScalar -> value.convertDependencyShort()
    is YAMLMapping -> value.convertDependencyFull()
    else -> null // Report wrong type
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLScalar.convertDependencyShort(): Dependency = when {
    textValue.startsWith("$") ->
        textValue.let { CatalogDependency().apply { ::catalogKey.convertSelf { textValue.removePrefix("$") } } }
    textValue.startsWith(".") -> textValue.let { InternalDependency().apply { ::path.convertSelf { asAbsolutePath() } } }
    else -> textValue.let { ExternalMavenDependency().apply { ::coordinates.convertSelf { textValue } } }
}.adjustTrace(this)

context(ProblemReporterContext, ConvertCtx)
private fun YAMLMapping.convertDependencyFull(): Dependency? = when {
    getYAMLElements().size > 1 -> null // TODO("report")
    getYAMLElements().isEmpty() -> null // TODO("report")
    keyValues.first()?.keyText?.startsWith("$") == true -> keyValues.first().convertCatalogDep()
    keyValues.first()?.keyText?.startsWith(".") == true -> keyValues.first().convertInternalDep()
    else -> keyValues.first().convertExternalMavenDep()
}?.adjustTrace(this)

context(ConvertCtx, ProblemReporterContext)
private fun YAMLKeyValue.convertCatalogDep(): CatalogDependency = CatalogDependency().apply {
    ::catalogKey.convertSelf { keyText.removePrefix("$") }
    value?.convertScopes(this)
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLKeyValue.convertInternalDep(): InternalDependency = InternalDependency().apply {
    ::path.convertSelf { asAbsolutePath() }
    value?.convertScopes(this)
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLKeyValue.convertExternalMavenDep() = ExternalMavenDependency().apply {
    ::coordinates.convertSelf { keyText }
    value?.convertScopes(this)
}

context(ConvertCtx, ProblemReporterContext)
private fun YAMLValue.convertScopes(dep: Dependency) = with(dep) {
    when {
        this@convertScopes is YAMLScalar && textValue == "exported" -> ::exported.convertSelf { true }
        this@convertScopes is YAMLScalar -> ::scope.convertSelf { convertEnum(DependencyScope) }
        this@convertScopes is YAMLMapping -> {
            ::scope.convertChildEnum(DependencyScope)
            ::exported.convertChildBoolean()
        }
        else -> Unit
    }
}