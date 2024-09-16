/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.applyPsiTrace
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
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.schema.Repository
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schema.TaskSettings
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx

context(Converter)
internal fun MappingNode.convertProject() = Project().apply {
    convertChildScalarCollection(::modules) { asTraceableString() }
}

context(Converter)
internal fun MappingNode.convertTemplate() = Template().apply {
    convertBase(this@apply)
}

context(Converter)
internal fun MappingNode.convertModule() = Module().apply a@ {
    convertChild(::product) { convertProduct() }
    convertChildScalarCollection(this@a::apply) {
        asAbsolutePath().asTraceable().applyPsiTrace(this.sourceElement)
    }
    convertChild(::aliases) {
        value?.asSequenceNode()?.convertScalarKeyedMap {
            asSequenceNode()
                ?.asScalarSequenceNode()
                ?.mapNotNull { it.convertEnum(Platform)?.asTraceable()?.applyPsiTrace(this) }
                ?.toSet()
        }.orEmpty() + value?.asMappingNode()?.keyValues?.mapNotNull { kv ->
            kv.keyText?.let {
                it to kv.value?.asSequenceNode()?.items?.mapNotNull {
                    it.asScalarNode().convertEnum(Platform)?.asTraceable()?.applyPsiTrace(this)
                }?.toSet()
            }
        }.orEmpty().toMap()
    }
    convertChildMapping(::module) { convertMeta() }
    convertBase(this@a)
}

context(Converter)
private fun <T : Base> MappingNode.convertBase(base: T) = base.apply {
    convertChild(::repositories) { convertRepositories() }
    convertModifierAware(::dependencies) { convertDependencies() }
    convertModifierAware(::`test-dependencies`) { convertDependencies() }

    convertModifierAware(::settings, Settings()) { convertSettings() }
    convertModifierAware(::`test-settings`, Settings()) { convertSettings() }

    convertChild(::tasks) { convertTasks() }
}

context(Converter)
private fun MappingEntry.convertTasks(): Map<String, TaskSettings>? {
    // TODO Report wrong type.
    val yamlMapping = this.value?.asMappingNode() ?: return null
    return yamlMapping.keyValues.mapNotNull { it.convertTask() }.toMap()
}

context(Converter)
private fun MappingEntry.convertTask(): Pair<String, TaskSettings>? {
    // TODO Report empty/missing key
    val taskName = keyText
    if (taskName.isNullOrEmpty()) return null

    // TODO Report wrong structure
    val settings = (value?.asMappingNode())?.convertTaskSettings() ?: return null

    return taskName to settings
}

context(Converter)
private fun MappingNode.convertTaskSettings(): TaskSettings {
    val settings = TaskSettings()
    for (item in keyValues) {
        if (item.keyText != "dependsOn") continue
        val value = item.value?.asSequenceNode() ?: continue
        val dependsOn = value.items.mapNotNull { dep ->
            dep.asScalarNode()?.textValue?.let {
                TraceableString(it).applyPsiTrace(dep)
            }
        }
        settings.dependsOn = dependsOn
    }
    return settings
}

context(Converter)
private fun MappingEntry.convertProduct() = ModuleProduct().apply {
    val value = this@convertProduct.value
    value?.asMappingNode()?.apply {
        convertChildEnum(::type, ProductType, isFatal = true, isLong = true)
        convertChildScalarCollection(::platforms) { convertEnum(Platform)?.asTraceable()?.applyPsiTrace(this.sourceElement) }
    } ?:
    value?.asScalarNode()?.apply {
       convertSelf(::type) { convertEnum(ProductType, isFatal = true, isLong = true) }
    } ?: value?.let {
        SchemaBundle.reportBundleError(
            node = value,
            messageKey = "unexpected.product.node.type",
            value::class.simpleName,
        )
    }
}

context(Converter)
private fun MappingNode.convertMeta() = Meta().apply {
    convertChildEnum(::layout, AmperLayout)
}

context(Converter)
private fun MappingEntry.convertRepositories(): List<Repository>? {
    // TODO Report wrong type.
    MappingNode.from(this.value)?.keyValues?.mapNotNull {
        it.value?.convertRepository() ?: it.keyElement?.convertRepository()
    }?.let {
        return it
    }

    return Sequence.from(this.value)?.let {
        it.items.mapNotNull { it.convertRepository() }
    }
}

context(Converter)
private fun PsiElement.convertRepository(): Repository? =
    (this.asMappingNode()?.convertRepositoryFull()
        ?: this.asScalarNode()?.convertRepositoryShort())
        ?.applyPsiTrace(this)

context(Converter)
private fun Scalar.convertRepositoryShort() = Repository().apply {
    convertSelf(::url) { textValue }
    convertSelf(::id) { textValue }
}

context(Converter)
private fun MappingNode.convertRepositoryFull(): Repository = Repository().also {
    convertChildString(it::url)
    convertChildString(it::id)
    convertChildBoolean(it::publish)
    convertChildBoolean(it::resolve)
    convertChildMapping(it::credentials) {
        Repository.Credentials().apply {
            // TODO Report non existent path.
            convertChildScalar(::file) { asAbsolutePath() }
            convertChildString(::usernameKey)
            convertChildString(::passwordKey)
        }
    }
}

context(Converter)
private fun MappingEntry.convertDependencies() =
    value?.asMappingNode()?.keyValues?.map { it.convertDependencyFull().applyPsiTrace(it) } ?:
    value?.asSequenceNode()?.items?.mapNotNull { it.convertDependency()?.applyPsiTrace(it) }


context(Converter)
private fun PsiElement.convertDependency(): Dependency? =
    this.asMappingNode()?.convertDependencyFull()
        ?: this.asScalarNode()?.convertDependencyShort()

context(Converter)
private fun Scalar.convertDependencyShort(): Dependency = when {
    textValue.startsWith("$") ->
        textValue.let { CatalogDependency().apply { convertSelf(::catalogKey) { textValue.removePrefix("$") } } }
    textValue.startsWith(".") -> textValue.let { InternalDependency().apply { convertSelf(::path) { asAbsolutePath() } } }
    else -> textValue.let { ExternalMavenDependency().apply { convertSelf(::coordinates) { textValue } } }
}.applyPsiTrace(this.sourceElement)

context(Converter)
private fun MappingNode.convertDependencyFull(): Dependency? = keyValues.singleOrNull()?.convertDependencyFull()

context(Converter)
private fun MappingEntry.convertDependencyFull(): Dependency = when {
    keyText?.startsWith("$") == true -> convertCatalogDep()
    keyText?.startsWith(".") == true -> convertInternalDep()
    else -> convertExternalMavenDep()
}.applyPsiTrace(keyElement)

context(Converter)
private fun MappingEntry.convertCatalogDep(): CatalogDependency = CatalogDependency().apply {
    convertEntryItself(::catalogKey) { keyText!!.removePrefix("$") }
    value?.convertScopes(this)
}

context(Converter)
private fun MappingEntry.convertInternalDep(): InternalDependency = InternalDependency().apply {
    convertEntryItself(::path) { asAbsolutePath() }
    value?.convertScopes(this)
}

context(Converter)
private fun MappingEntry.convertExternalMavenDep() = ExternalMavenDependency().apply {
    convertEntryItself(::coordinates) { keyText }
    value?.convertScopes(this)
}

context(Converter)
private fun PsiElement.convertScopes(dep: Dependency) = with(dep) {
    asScalarNode()?.apply {
        if (textValue == "exported") convertSelf(::exported) { true }
        else convertSelf(::scope) { convertEnum(DependencyScope) }
    }
    asMappingNode()?.apply {
        convertChildEnum(::scope, DependencyScope)
        convertChildBoolean(::exported)
    }
}
