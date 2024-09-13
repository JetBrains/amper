/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.ProblemReporterContext
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

context(ProblemReporterContext)
internal fun PsiElement.convertProject() = Project().apply {
    val documentMapping = asMappingNode() ?: return@apply
    with(documentMapping) {
        ::modules.convertChildScalarCollection { asTraceableString() }
    }
}

context(ProblemReporterContext, ConvertCtx)
internal fun PsiElement.convertTemplate() = Template().apply {
    val documentMapping = asMappingNode() ?: return@apply
    with(documentMapping) {
        convertBase(this@apply)
    }
}

context(ProblemReporterContext, ConvertCtx)
internal fun PsiElement.convertModule() = Module().apply {
    val documentMapping = asMappingNode() ?: return@apply
    with(documentMapping) {
        ::product.convertChild { convertProduct() }
        this@apply::apply.convertChildScalarCollection { asAbsolutePath().asTraceable().applyPsiTrace(this.sourceElement) }
        ::aliases.convertChild {
            asSequenceNode()?.convertScalarKeyedMap {
                asSequenceNode()
                    ?.asScalarSequenceNode()
                    ?.mapNotNull { it.convertEnum(Platform)?.asTraceable()?.applyPsiTrace(this) }
                    ?.toSet()
            }
        }
        ::module.convertChild { asMappingNode()?.convertMeta() }
        convertBase(this@apply)
    }
}

context(ProblemReporterContext, ConvertCtx)
private fun <T : Base> MappingNode.convertBase(base: T) = base.apply {
    ::repositories.convertChild { convertRepositories() }

    ::dependencies.convertModifierAware { value?.convertDependencies() }
    ::`test-dependencies`.convertModifierAware { value?.convertDependencies() }

    ::settings.convertModifierAware(Settings()) { value?.convertSettings() }
    ::`test-settings`.convertModifierAware(Settings()) { value?.convertSettings() }

    ::tasks.convertChild { convertTasks() }
}

context(ProblemReporterContext, ConvertCtx)
private fun MappingEntry.convertTasks(): Map<String, TaskSettings>? {
    // TODO Report wrong type.
    val yamlMapping = this.value?.asMappingNode() ?: return null
    return yamlMapping.keyValues.mapNotNull { it.convertTask() }.toMap()
}

context(ProblemReporterContext, ConvertCtx)
private fun MappingEntry.convertTask(): Pair<String, TaskSettings>? {
    // TODO Report empty/missing key
    val taskName = keyText
    if (taskName.isNullOrEmpty()) return null

    // TODO Report wrong structure
    val settings = (value?.asMappingNode())?.convertTaskSettings() ?: return null

    return taskName to settings
}

context(ProblemReporterContext, ConvertCtx)
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

context(ProblemReporterContext, ConvertCtx)
private fun MappingEntry.convertProduct() = ModuleProduct().apply {
    val value = this@convertProduct.value
    value?.asMappingNode()?.let {
        with(it) {
            ::type.convertChildEnum(ProductType, isFatal = true, isLong = true)
            ::platforms.convertChildScalarCollection { convertEnum(Platform)?.asTraceable()?.applyPsiTrace(this.sourceElement) }
        }
    } ?:
    value?.asScalarNode()?.let {
        with(it) {
            ::type.convertSelf { it.convertEnum(ProductType, isFatal = true, isLong = true) }
        }
    } ?: value?.let {
        SchemaBundle.reportBundleError(
            node = value,
            messageKey = "unexpected.product.node.type",
            value::class.simpleName,
        )
    }
}

context(ConvertCtx, ProblemReporterContext)
private fun MappingNode.convertMeta() = Meta().apply {
    ::layout.convertChildEnum(AmperLayout)
}

context(ProblemReporterContext, ConvertCtx)
private fun MappingEntry.convertRepositories(): List<Repository>? {
    (Sequence.from(this.value))?.let {
        return it.items.mapNotNull { it.convertRepository() }
    }
    // TODO Report wrong type.
    return null
}

context(ProblemReporterContext, ConvertCtx)
private fun PsiElement.convertRepository(): Repository? =
    (this.asMappingNode()?.convertRepositoryFull()
        ?: this.asScalarNode()?.convertRepositoryShort())
        ?.applyPsiTrace(this)

context(ProblemReporterContext, ConvertCtx)
private fun Scalar.convertRepositoryShort() = Repository().apply {
    ::url.convertSelf { textValue }
    ::id.convertSelf { textValue }
}

context(ProblemReporterContext, ConvertCtx)
private fun MappingNode.convertRepositoryFull(): Repository = Repository().apply {
    ::url.convertChildString()
    ::id.convertChildString()
    ::publish.convertChildBoolean()
    ::resolve.convertChildBoolean()
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
private fun PsiElement.convertDependencies() =
    asSequenceNode()!!.items.mapNotNull { it.convertDependency() }


context(ProblemReporterContext, ConvertCtx)
private fun PsiElement.convertDependency(): Dependency? =
    this.asMappingNode()?.convertDependencyFull()
        ?: this.asScalarNode()?.convertDependencyShort()

context(ProblemReporterContext, ConvertCtx)
private fun Scalar.convertDependencyShort(): Dependency = when {
    textValue.startsWith("$") ->
        textValue.let { CatalogDependency().apply { ::catalogKey.convertSelf { textValue.removePrefix("$") } } }
    textValue.startsWith(".") -> textValue.let { InternalDependency().apply { ::path.convertSelf { asAbsolutePath() } } }
    else -> textValue.let { ExternalMavenDependency().apply { ::coordinates.convertSelf { textValue } } }
}.applyPsiTrace(this.sourceElement)

context(ProblemReporterContext, ConvertCtx)
private fun MappingNode.convertDependencyFull(): Dependency? = when {
    //getYAMLElements().size > 1 -> null // TODO("report")
    //getYAMLElements().isEmpty() -> null // TODO("report")
    keyValues.first()?.keyText?.startsWith("$") == true -> keyValues.first().convertCatalogDep()
    keyValues.first()?.keyText?.startsWith(".") == true -> keyValues.first().convertInternalDep()
    else -> keyValues.first().convertExternalMavenDep()
}?.applyPsiTrace(this.keyValues.first().keyElement)

context(ConvertCtx, ProblemReporterContext)
private fun MappingEntry.convertCatalogDep(): CatalogDependency = CatalogDependency().apply {
    ::catalogKey.convertEntryItself { keyText!!.removePrefix("$") }
    value?.convertScopes(this)
}

context(ProblemReporterContext, ConvertCtx)
private fun MappingEntry.convertInternalDep(): InternalDependency = InternalDependency().apply {
    ::path.convertEntryItself { asAbsolutePath() }
    value?.convertScopes(this)
}

context(ProblemReporterContext, ConvertCtx)
private fun MappingEntry.convertExternalMavenDep() = ExternalMavenDependency().apply {
    ::coordinates.convertEntryItself { keyText }
    value?.convertScopes(this)
}

context(ConvertCtx, ProblemReporterContext)
private fun PsiElement.convertScopes(dep: Dependency) = with(dep) {
    this@convertScopes.asScalarNode()?.let {
        with(it) {
            if (it.textValue == "exported") ::exported.convertSelf { true }
            else ::scope.convertSelf { convertEnum(DependencyScope) }
        }
    }
    this@convertScopes.asMappingNode()?.let {
        with(it) {
            ::scope.convertChildEnum(DependencyScope)
            ::exported.convertChildBoolean()
        }
    }
}
