/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.adjustTrace
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
import org.jetbrains.amper.frontend.schema.noModifiers
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.NodeTuple
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import java.io.Reader
import java.nio.file.Path


// TODO Rethink.
data class ConvertCtx(
    val basePath: Path
)

context(ConvertCtx, ProblemReporterContext)
fun convertModuleViaSnake(reader: () -> Reader): Module {
    val yaml = Yaml()
    val rootNode = yaml.compose(reader())
    // TODO Add reporting.
    if (rootNode !is MappingNode) return Module()
    return rootNode.convertModuleViaSnake()
}

context(ConvertCtx, ProblemReporterContext)
fun convertTemplateViaSnake(reader: () -> Reader): Template {
    val yaml = Yaml()
    val rootNode = yaml.compose(reader())
    if (rootNode !is MappingNode) return Template()
    return rootNode.convertBase(Template())
}

context(ConvertCtx, ProblemReporterContext)
private fun MappingNode.convertModuleViaSnake() = Module().apply {
    product(tryGetChildNode("product")?.convertProduct()) { /* TODO report */ }
    apply(tryGetScalarSequenceNode("apply")?.map { it.asAbsolutePath() /* TODO check path */ })
    aliases(tryGetMappingNode("aliases")?.convertScalarKeyedMap { _ ->
        // TODO Report non enum value.
        asScalarSequenceNode()?.mapNotNull { it.convertEnum(Platform) }?.toSet()
    })
    module(tryGetMappingNode("module")?.convertMeta())
    convertBase(this)
}

context(ConvertCtx, ProblemReporterContext)
private fun <T : Base> MappingNode.convertBase(base: T) = base.apply {
    repositories(tryGetMappingNode("repositories")?.convertRepositories())

    dependencies(convertWithModifiers("dependencies") { it.convertDependencies() })
    settings(convertWithModifiers("settings") { asMappingNode()?.convertSettings() })
    `test-dependencies`(convertWithModifiers("test-dependencies") { it.convertDependencies() })

    settings(convertWithModifiers("settings") { it.convertSettings() }.apply {
        // Here we must add root settings to take defaults from them.
        computeIfAbsent(noModifiers) { Settings() }
    })
    `test-settings`(convertWithModifiers("test-settings") { it.asMappingNode()?.convertSettings() }.apply {
        // Here we must add root settings to take defaults from them.
        computeIfAbsent(noModifiers) { Settings() }
    })
}

context(ConvertCtx, ProblemReporterContext)
private fun Node.convertProduct() = ModuleProduct().apply {
    when (this@convertProduct) {
        is MappingNode -> {
            type(tryGetScalarNode("type")?.convertEnum(ProductType, isFatal = true, isLong = true))
                .adjustTrace(tryGetScalarNode("type"))
            val platformsNode = tryGetScalarSequenceNode("platforms")
            platforms(platformsNode?.mapNotNull { it.convertEnum(Platform) /* TODO report */ })
                .adjustTrace(tryGetChildNode("platforms"))
        }

        is ScalarNode -> type(this@convertProduct.convertEnum(ProductType, isFatal = true, isLong = true))
            .adjustTrace(this@convertProduct)
        else -> TODO("report")
    }
}

context(ConvertCtx, ProblemReporterContext)
private fun MappingNode.convertMeta() = Meta().apply {
    layout(tryGetScalarNode("layout")?.convertEnum(AmperLayout))
}

context(ConvertCtx, ProblemReporterContext)
private fun Node.convertRepositories(): List<Repository>? {
    if (this@convertRepositories !is SequenceNode) return null
    // TODO Report wrong type.
    return value.mapNotNull { it.convertRepository() }
}

context(ConvertCtx, ProblemReporterContext)
private fun Node.convertRepository() = when (this) {
    is ScalarNode -> Repository().apply {
        url(value)
        id(value)
    }

    is MappingNode -> Repository().apply {
        url(tryGetScalarNode("url")?.value) { /* TODO report */ }
        id(tryGetScalarNode("id")?.value)
        // TODO Report wrong type. Introduce helper for boolean maybe?
        publish(tryGetScalarNode("publish")?.value?.toBoolean())

        credentials(
            tryGetMappingNode("credentials")?.let {
                Repository.Credentials().apply {
                    // TODO Report non existent path.
                    file(tryGetScalarNode("file")?.asAbsolutePath()) { /* TODO report */ }
                    usernameKey(tryGetScalarNode("usernameKey")?.value) { /* TODO report */ }
                    passwordKey(tryGetScalarNode("passwordKey")?.value) { /* TODO report */ }
                }
            }
        )
    }

    else -> null
}

context(ConvertCtx, ProblemReporterContext)
private fun Node.convertDependencies() = when(this) {
    is SequenceNode -> value.mapNotNull { it.convertDependency() }
    is ScalarNode -> if (value.isBlank()) emptyList() else null // TODO Report non-null scalar
    else -> null // TODO Report wrong type.
}

context(ConvertCtx, ProblemReporterContext)
private fun Node.convertDependency(): Dependency? {
    val node = this
    return if (this is ScalarNode) {
        when {
            // TODO Report non existent path.
            value.startsWith(".") -> value?.let { InternalDependency().apply { path(it.asAbsolutePath()) } }
            // TODO Report non existent path.
//            value.startsWith("$") -> value
//                ?.let { CatalogDependency().apply { catalogKey(it.removePrefix("$")).adjustTrace(node) } }

            else -> value?.let { ExternalMavenDependency().apply { coordinates(it) } }

}
    } else {
        when {    this is MappingNode && value.size > 1 -> TODO("report")
    this is MappingNode && value.isEmpty() -> TODO("report")
//            this is MappingNode && value.first().keyNode.asScalarNode()?.value?.startsWith("$") == true ->
//                value.first().convertCatalogDep()

            this is MappingNode && value.first().keyNode.asScalarNode()?.value?.startsWith(".") == true ->
                value.first().convertInternalDep()

            this is MappingNode && value.first().keyNode.asScalarNode()?.value != null ->
                value.first().convertExternalMavenDep()

            else -> null // Report wrong type
        }
    }
}

context(ConvertCtx, ProblemReporterContext)
private fun NodeTuple.convertExternalMavenDep() = ExternalMavenDependency().apply {
    coordinates(keyNode.asScalarNode(true)!!.value)
    convertScopes(this)
}

context(ConvertCtx, ProblemReporterContext)
private fun NodeTuple.convertInternalDep(): InternalDependency = InternalDependency().apply {
    path(keyNode.asScalarNode(true)!!.asAbsolutePath())
    convertScopes(this)
}

context(ConvertCtx, ProblemReporterContext)
private fun NodeTuple.convertCatalogDep(): CatalogDependency = CatalogDependency().apply {
    catalogKey(keyNode.asScalarNode(true)?.value?.removePrefix("$"))
    convertScopes(this)
}

context(ConvertCtx, ProblemReporterContext)
private fun NodeTuple.convertScopes(dep: Dependency) = with(dep) {
    val valueNode = valueNode
    when {
        valueNode is ScalarNode && valueNode.value == "exported" -> exported(true)
        valueNode is ScalarNode -> scope(valueNode.convertEnum(DependencyScope))
        valueNode is MappingNode -> {
            scope(valueNode.tryGetScalarNode("scope")?.convertEnum(DependencyScope))
            exported(valueNode.tryGetScalarNode("exported")?.value?.toBoolean())
        }

        else -> error("Unreachable")
    }
}