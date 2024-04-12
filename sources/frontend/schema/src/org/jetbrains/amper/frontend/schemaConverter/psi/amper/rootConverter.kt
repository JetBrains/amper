/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.amper

import com.intellij.amper.lang.AmperLiteral
import com.intellij.amper.lang.AmperObject
import com.intellij.amper.lang.AmperObjectElement
import com.intellij.amper.lang.AmperProperty
import com.intellij.amper.lang.AmperValue
import com.intellij.amper.lang.impl.allObjectElements
import com.intellij.amper.lang.impl.collectionItems
import com.intellij.amper.lang.impl.propertyList
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
import org.jetbrains.amper.frontend.schema.TaskSettings
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.psi.assertNodeType


context(ProblemReporterContext, ConvertCtx)
fun AmperObject.convertTemplate() = Template().apply {
    convertBase(this@apply)
}

context(ProblemReporterContext, ConvertCtx)
fun AmperObject.convertModule() = Module().apply {
    val documentMapping = this
    with(documentMapping) {
        ::product.convertChild { convertProduct() }
        this@apply::apply.convertChildScalarCollection { asAbsolutePath() }
        ::aliases.convertChild {
            (this.value as? AmperObject)?.propertyList?.associate {
                it.name to (it.value as? AmperObject)
                    ?.collectionItems
                    ?.filterIsInstance<AmperLiteral>()
                    ?.mapNotNull { it.convertEnum(Platform)?.asTraceable()?.adjustTrace(this) }
                    ?.toSet()
            }.orEmpty()
        }
        ::module.convertChild { (value as? AmperObject)?.convertMeta() }
        convertBase(this@apply)
    }
}

context(ProblemReporterContext, ConvertCtx)
internal fun <T : Base> AmperObject.convertBase(base: T) = base.apply {
    ::repositories.convertChild { convertRepositories() }

    ::dependencies.convertModifierAware { value?.convertDependencies() }
    ::`test-dependencies`.convertModifierAware { value?.convertDependencies() }

    ::settings.convertModifierAware(Settings()) { (value as? AmperObject)?.convertSettings() }
    ::`test-settings`.convertModifierAware(Settings()) { (value as? AmperObject)?.convertSettings() }

    ::tasks.convertChild { convertTasks() }
}

context(ProblemReporterContext, ConvertCtx)
private fun AmperProperty.convertTasks(): Map<String, TaskSettings>? {
    // TODO Report wrong type.
    val amperObject = this.value as? AmperObject ?: return null
    return amperObject.objectElementList.mapNotNull { it.convertTask() }.toMap()
}

context(ProblemReporterContext, ConvertCtx)
private fun AmperObjectElement.convertTask(): Pair<String, TaskSettings>? {
    val property = this as? AmperProperty ?: return null
    val taskName = property.name
    if (taskName.isNullOrEmpty()) return null
    val settings = (property.value as? AmperObject)?.convertTaskSettings() ?: return null
    return taskName to settings
}

context(ProblemReporterContext, ConvertCtx)
private fun AmperObject.convertTaskSettings(): TaskSettings {
    val settings = TaskSettings()
    for (item in objectElementList) {
        val property = item as? AmperProperty ?: continue
        if (property.name == "dependsOn") {
            val value = property.value as? AmperObject ?: continue
            val dependsOn = value.objectElementList.mapNotNull { (it as? AmperProperty)?.name }
            settings.dependsOn = dependsOn
        }
    }
    return settings
}

context(ProblemReporterContext, ConvertCtx)
private fun AmperProperty.convertProduct() = ModuleProduct().apply {
    when (val productNodeValue = this@convertProduct.value) {
        is AmperObject -> with(productNodeValue) {
            ::type.convertChildEnum(ProductType, isFatal = true, isLong = true)
            ::platforms.convertChildScalarCollection { convertEnum(Platform)?.asTraceable()?.adjustTrace(this) }
        }
        is AmperLiteral -> ::type.convertSelf { productNodeValue.convertEnum(ProductType, isFatal = true, isLong = true) }
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
private fun AmperObject.convertMeta() = Meta().apply {
    ::layout.convertChildEnum(AmperLayout)
}

context(ProblemReporterContext, ConvertCtx)
private fun AmperProperty.convertRepositories(): List<Repository>? {
    (this.value as? AmperObject)?.let {
        return it.objectElementList.mapNotNull { it.convertRepository() }
    }
    // TODO Report wrong type.
    return null
}

context(ProblemReporterContext, ConvertCtx)
private fun AmperObjectElement.convertRepository(): Repository? = when (val value = this) {
    is AmperProperty -> (value.value as? AmperObject)?.convertRepositoryFull() ?: (
            (value.nameElement as? AmperLiteral)?.convertRepositoryShort())
    else -> null
}?.adjustTrace(this)

context(ProblemReporterContext, ConvertCtx)
private fun AmperLiteral.convertRepositoryShort() = Repository().apply {
    ::url.convertSelf { textValue }
    ::id.convertSelf { textValue }
}

context(ProblemReporterContext, ConvertCtx)
private fun AmperObject.convertRepositoryFull(): Repository = Repository().apply {
    ::url.convertChildString()
    ::id.convertChildString()
    ::publish.convertChildBoolean()
    ::resolve.convertChildBoolean()
    ::credentials.convertChild {
        (value as? AmperObject)?.run {
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
private fun AmperValue.convertDependencies() =
    assertNodeType<AmperObject, List<Dependency>>("dependencies") {
        allObjectElements.mapNotNull {
            when {
                it is AmperProperty && it.value == null -> (it.nameElement as? AmperLiteral)?.convertDependencyShort()
                it is AmperProperty -> it.convertDependencyFull()
                else -> null
            }
        }
    }

context(ProblemReporterContext, ConvertCtx)
private fun AmperLiteral.convertDependencyShort(): Dependency {
    val textValue = textValue
    return when {
        textValue.startsWith("$") ->
            textValue.let { CatalogDependency().apply { ::catalogKey.convertSelf { textValue.removePrefix("$") } } }

        textValue.startsWith(".") -> textValue.let { InternalDependency().apply { ::path.convertSelf { asAbsolutePath() } } }
        else -> textValue.let { ExternalMavenDependency().apply { ::coordinates.convertSelf { textValue } } }
    }.adjustTrace(this)
}

context(ProblemReporterContext, ConvertCtx)
private fun AmperProperty.convertDependencyFull(): Dependency = when {
    name?.startsWith("$") == true -> convertCatalogDep()
    name?.startsWith(".") == true -> convertInternalDep()
    else -> convertExternalMavenDep()
}.adjustTrace(this)

context(ConvertCtx, ProblemReporterContext)
private fun AmperProperty.convertCatalogDep(): CatalogDependency = CatalogDependency().apply {
    ::catalogKey.convertSelf { name?.removePrefix("$") }
    value?.convertScopes(this)
}

context(ProblemReporterContext, ConvertCtx)
private fun AmperProperty.convertInternalDep(): InternalDependency = InternalDependency().apply {
    ::path.convertSelf { asAbsolutePath() }
    value?.convertScopes(this)
}

context(ProblemReporterContext, ConvertCtx)
private fun AmperProperty.convertExternalMavenDep() = ExternalMavenDependency().apply {
    ::coordinates.convertSelf { name }
    value?.convertScopes(this)
}

context(ConvertCtx, ProblemReporterContext)
private fun AmperValue.convertScopes(dep: Dependency) = with(dep) {
    when {
        this@convertScopes is AmperLiteral && textValue == "exported" -> ::exported.convertSelf { true }
        this@convertScopes is AmperLiteral -> ::scope.convertSelf { convertEnum(DependencyScope) }
        this@convertScopes is AmperObject -> {
            ::scope.convertChildEnum(DependencyScope)
            ::exported.convertChildBoolean()
        }
        else -> Unit
    }
}
