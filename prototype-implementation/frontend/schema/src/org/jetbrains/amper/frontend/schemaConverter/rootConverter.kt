/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.schema.*
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.NodeTuple
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import java.nio.file.Path
import kotlin.io.path.reader

context(ProblemReporterContext)
fun convertModuleViaSnake(file: Path): Module {
    val yaml = Yaml()
    val rootNode = yaml.compose(file.reader())
    // TODO Add reporting.
    if (rootNode !is MappingNode) return Module()
    return rootNode.convertModuleViaSnake()
}

context(ProblemReporterContext)
fun convertTemplateViaSnake(file: Path): Template {
    val yaml = Yaml()
    val rootNode = yaml.compose(file.reader())
    if (rootNode !is MappingNode) return Template()
    return rootNode.convertBase(Template())
}

context(ProblemReporterContext)
private fun MappingNode.convertModuleViaSnake() = Module().apply {
    product(tryGetMappingNode("product")?.convertProduct()) { /* TODO report */ }
    apply(tryGetScalarSequenceNode("apply")?.map { it.asAbsolutePath() /* TODO check path */ })
    aliases(tryGetMappingNode("aliases")?.convertScalarKeyedMap { _ ->
        // TODO Report non enum value.
        asScalarSequenceNode()?.map { it.convertEnum(Platform) }?.toSet()
    })
    module(tryGetMappingNode("module")?.convertMeta())
    convertBase(this)
}

context(ProblemReporterContext)
private fun <T : Base> MappingNode.convertBase(base: T) = base.apply {
    repositories(tryGetMappingNode("repositories")?.convertRepositories())
    dependencies(convertWithModifiers("dependencies") { convertDependencies() })
    settings(convertWithModifiers("settings") { asMappingNode()?.convertSettings() })
    `test-dependencies`(convertWithModifiers("test-dependencies") { convertDependencies() })
    `test-settings`(convertWithModifiers("test-settings") { asMappingNode()?.convertSettings() })
}

context(ProblemReporterContext)
private fun MappingNode.convertProduct() = ModuleProduct().apply {
    type(tryGetScalarNode("type")?.convertEnum(ProductType)) { /* TODO report */ }
    platforms(tryGetScalarSequenceNode("platforms")
        ?.mapNotNull { it.convertEnum(Platform) /* TODO report */ }
    )
}

context(ProblemReporterContext)
private fun MappingNode.convertMeta() = Meta().apply {
    layout(tryGetScalarNode("layout")?.convertEnum(AmperLayout))
}

context(ProblemReporterContext)
private fun Node.convertRepositories(): List<Repository>? {
    if (this@convertRepositories !is SequenceNode) return null
    // TODO Report wrong type.
    return value.mapNotNull { it.convertRepository() }
}

context(ProblemReporterContext)
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

context(ProblemReporterContext)
private fun Node.convertDependencies() = when(this) {
    is SequenceNode -> value.mapNotNull { it.convertDependency() }
    is ScalarNode -> if (value.isBlank()) emptyList() else null // TODO Report non-null scalar
    else -> null // TODO Report wrong type.
}

context(ProblemReporterContext)
private fun Node.convertDependency(): Dependency? {
    val node = this
    return if (this is ScalarNode) {
        when {
            // TODO Report non existent path.
            value.startsWith(".") -> value
                ?.let { InternalDependency().apply { path(it.asAbsolutePath()) } }

            // TODO Report non existent path.
//            value.startsWith("$") -> value
//                ?.let { CatalogDependency().apply { catalogKey(it.removePrefix("$")).adjustTrace(node) } }

            else -> value?.let { ExternalMavenDependency().apply { coordinates(it) } }
        }
    } else {
        when {
            this is MappingNode && value.size > 1 -> TODO("report")
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

context(ProblemReporterContext)
private fun NodeTuple.convertExternalMavenDep() = ExternalMavenDependency().apply {
    coordinates(keyNode.asScalarNode(true)!!.value)
    convertScopes(this)
}

context(ProblemReporterContext)
private fun NodeTuple.convertInternalDep(): InternalDependency = InternalDependency().apply {
    path(keyNode.asScalarNode(true)!!.asAbsolutePath())
    convertScopes(this)
}

context(ProblemReporterContext)
private fun NodeTuple.convertScopes(dep: Dependency) = with(dep) {
    val valueNode = valueNode
    when {
        valueNode is ScalarNode && valueNode.value == "compile-only" -> `compile-only`(true)
        valueNode is ScalarNode && valueNode.value == "runtime-only" -> `runtime-only`(true)
        valueNode is ScalarNode && valueNode.value == "exported" -> exported(true)
        valueNode is MappingNode -> {
            `compile-only`(valueNode.tryGetScalarNode("compile-only")?.value?.toBoolean())
            `runtime-only`(valueNode.tryGetScalarNode("runtime-only")?.value?.toBoolean())
            exported(valueNode.tryGetScalarNode("exported")?.value?.toBoolean())
        }
    }
}