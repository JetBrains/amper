package org.jetbrains.amper.frontend.schemaConverter.psi

/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.schema.*
import org.jetbrains.amper.frontend.schemaConverter.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.adjustTrace
import org.jetbrains.amper.frontend.schemaConverter.tryGetSequenceNode
import org.jetbrains.yaml.psi.*

context(ProblemReporterContext, ConvertCtx)
public fun YAMLDocument.convertTemplate() = Template().apply {
  val documentMapping = getTopLevelValue()?.asMappingNode()!!
  convertBase(this)
}

context(ProblemReporterContext, ConvertCtx)
public fun YAMLDocument.convertModule() = Module().apply {
  val documentMapping = getTopLevelValue()?.asMappingNode()!!
  with (documentMapping) {
    product(tryGetChildElement("product")?.convertProduct()) { /* TODO report */ }
    apply(tryGetScalarSequenceNode("apply")?.map { it.asAbsolutePath() /* TODO check path */ })
    aliases(tryGetMappingNode("aliases")
      ?.convertScalarKeyedMap {
        asSequenceNode()?.asScalarSequenceNode()?.mapNotNull { it.convertEnum(Platform) }?.toSet()
      }
    ).adjustTrace(tryGetMappingNode("aliases"))
    module(tryGetMappingNode("module")?.convertMeta())
    convertBase(this@apply)
  }
}

context(ProblemReporterContext, ConvertCtx)
internal fun <T : Base> YAMLDocument.convertBase(base: T) = base.apply {
  getTopLevelValue()?.asMappingNode()?.let { documentMapping ->
    with (documentMapping) {
      repositories(tryGetChildElement("repositories")?.convertRepositories()).adjustTrace(tryGetChildElement("repositories"))
      dependencies(convertWithModifiers("dependencies") { convertDependencies() })
      settings(convertWithModifiers("settings") { asMappingNode()?.convertSettings() }.apply {
        // Here we must add root settings to take defaults from them.
        computeIfAbsent(noModifiers) { Settings() }
      }) {}
      `test-dependencies`(convertWithModifiers("test-dependencies") { convertDependencies() })
      `test-settings`(convertWithModifiers("test-settings") { asMappingNode()?.convertSettings() }
        .apply {
          // Here we must add root settings to take defaults from them.
          computeIfAbsent(noModifiers) { Settings() }
        })
    }
  }
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLKeyValue.convertProduct() = ModuleProduct().apply {
  when (val productNodeValue = this@convertProduct.value) {
    is YAMLMapping -> {
      with(productNodeValue) {
        type(tryGetScalarNode("type"), ProductType, isFatal = true, isLong = true)
        platforms(tryGetScalarSequenceNode("platforms")?.mapNotNull { it.convertEnum(Platform) })
          .adjustTrace(tryGetChildNode("platforms"))
      }
    }

    is YAMLScalar -> type(productNodeValue, ProductType, isFatal = true, isLong = true)

    else -> TODO("report")
  }
}.adjustTrace(this)

context(ConvertCtx, ProblemReporterContext)
private fun YAMLMapping.convertMeta() = Meta().apply {
  layout(tryGetScalarNode("layout"), AmperLayout)
}.adjustTrace(this)

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
  is YAMLScalar -> Repository().apply {
    url(value)
    id(value)
  }

  is YAMLMapping -> Repository().apply {
    url(value.tryGetScalarNode("url")) { /* TODO report */ }
    id(value.tryGetScalarNode("id"))
    // TODO Report wrong type. Introduce helper for boolean maybe?
    publish(value.tryGetScalarNode("publish"))

    credentials(
      value.asMappingNode()?.tryGetMappingNode("credentials")?.let {
        Repository.Credentials().apply {
          // TODO Report non existent path.
          file(it.tryGetScalarNode("file")?.asAbsolutePath()) { /* TODO report */ }.adjustTrace(it.tryGetScalarNode("file"))
          usernameKey(it.tryGetScalarNode("usernameKey")) { /* TODO report */ }
          passwordKey(it.tryGetScalarNode("passwordKey")) { /* TODO report */ }
        }.adjustTrace(it)
      }
    )
  }

  else -> null
}?.adjustTrace(this.value)

context(ProblemReporterContext, ConvertCtx)
private fun YAMLValue.convertDependencies() = assertNodeType<YAMLSequence, List<Dependency>>("dependencies") {
  items.mapNotNull { it.convertDependency() }
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLSequenceItem.convertDependency(): Dependency? = when {
  this.value is YAMLScalar && this.value!!.text.startsWith("$") ->
    // TODO Report non existent path.
    this.value!!.text.let { CatalogDependency().apply { catalogKey(it.removePrefix("$")).adjustTrace(this@convertDependency.value) } }
  this.value is YAMLScalar && this.value!!.text.startsWith(".") ->
    // TODO Report non existent path.
    this.value!!.text.let { InternalDependency().apply { path(it.asAbsolutePath()) } }

  this.value is YAMLScalar ->
    this.value!!.let { ExternalMavenDependency().apply { coordinates(it as YAMLScalar) } }
  this.value is YAMLPsiElement && getYAMLElements().size > 1 -> TODO("report")
  this.value is YAMLPsiElement && getYAMLElements().isEmpty() -> TODO("report")
  this.value is YAMLMapping && (this.value as YAMLMapping).keyValues.first()?.keyText?.startsWith("$") == true ->
    (this.value as YAMLMapping).keyValues.first().convertCatalogDep()
  this.value is YAMLMapping && (this.value as YAMLMapping).keyValues.first()?.keyText?.startsWith(".") == true ->
    (this.value as YAMLMapping).keyValues.first().convertInternalDep()
  this.value is YAMLMapping && (this.value as YAMLMapping).keyValues.first().keyText != null ->
    (this.value as YAMLMapping).keyValues.first().convertExternalMavenDep()
  else -> null // Report wrong type
}?.adjustTrace(this.value)

context(ConvertCtx, ProblemReporterContext)
private fun YAMLKeyValue.convertCatalogDep(): CatalogDependency = CatalogDependency().apply {
  catalogKey(keyText.removePrefix("$")).adjustTrace(this@convertCatalogDep)
  convertScopes(this)
}

context(ConvertCtx, ProblemReporterContext)
private fun YAMLKeyValue.convertScopes(dep: Dependency) = with(dep) {
  val valueNode = value
  when {
    valueNode is YAMLScalar && valueNode.textValue == "exported" -> exported(true).adjustTrace(valueNode)
    valueNode is YAMLScalar -> scope(valueNode, DependencyScope)
    valueNode is YAMLMapping -> {
      scope(valueNode.tryGetScalarNode("scope"), DependencyScope)
      exported(valueNode.tryGetScalarNode("exported"))
    }

    else -> error("Unreachable")
  }
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLKeyValue.convertExternalMavenDep() = ExternalMavenDependency().apply {
  coordinates(keyText).adjustTrace(this@convertExternalMavenDep)
  adjustScopes(this)
}

context(ProblemReporterContext, ConvertCtx)
private fun YAMLKeyValue.convertInternalDep(): InternalDependency = InternalDependency().apply {
  path(keyText.asAbsolutePath())
  adjustScopes(this)
}

context(ProblemReporterContext)
private fun YAMLKeyValue.adjustScopes(dep: Dependency) = with(dep) {
  val valueNode = value
  when {
    valueNode is YAMLScalar && valueNode.textValue == "compile-only" -> scope(valueNode, DependencyScope)
    valueNode is YAMLScalar && valueNode.textValue == "runtime-only" -> scope(valueNode, DependencyScope)
    valueNode is YAMLScalar && valueNode.textValue == "exported" -> exported(true).adjustTrace(valueNode)
    valueNode is YAMLMapping -> {
      scope(valueNode.tryGetScalarNode("scope"), DependencyScope)
      exported(valueNode.tryGetScalarNode("exported"))
    }

    else -> Unit
  }
}

fun <T : Traceable> T.adjustTrace(it: YAMLPsiElement?) = apply { trace = it }