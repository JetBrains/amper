/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter

import com.intellij.openapi.project.Project
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.schema.*
import org.jetbrains.amper.frontend.schemaConverter.psi.adjustTrace
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.*
import java.io.Reader
import java.nio.file.Path

//// TODO Rethink.
data class ConvertCtx(
    val basePath: Path,
    val pathResolver: FrontendPathResolver
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
    repositories(tryGetSequenceNode("repositories")?.convertRepositories()).adjustTrace(tryGetSequenceNode("repositories"))

    dependencies(convertWithModifiers("dependencies") { it.convertDependencies() })
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
            type(tryGetScalarNode("type"), ProductType, isFatal = true, isLong = true)
            val platformsNode = tryGetScalarSequenceNode("platforms")
            platforms(platformsNode?.mapNotNull { it.convertEnum(Platform) })
                .adjustTrace(tryGetChildNode("platforms"))
        }

        is ScalarNode -> type(this@convertProduct, ProductType, isFatal = true, isLong = true)

        else -> TODO("report")
    }
}.adjustTrace(this)

context(ConvertCtx, ProblemReporterContext)
private fun MappingNode.convertMeta() = Meta().apply {
    layout(tryGetScalarNode("layout"), AmperLayout)
}.adjustTrace(this)

context(ConvertCtx, ProblemReporterContext)
private fun SequenceNode.convertRepositories(): List<Repository> {
    // TODO Report wrong type.
    return value.mapNotNull { it.convertRepository() }
}

context(ConvertCtx, ProblemReporterContext)
private fun Node.convertRepository() = when (val node = this) {
    is ScalarNode -> Repository().apply {
        url(node)
        id(node)
    }

    is MappingNode -> Repository().apply {
        url(node.tryGetScalarNode("url")) { /* TODO report */ }
        id(node.tryGetScalarNode("id"))
        // TODO Report wrong type. Introduce helper for boolean maybe?
        publish(node.tryGetScalarNode("publish"))

        credentials(
            node.tryGetMappingNode("credentials")?.let {
                Repository.Credentials().apply {
                    // TODO Report non existent path.
                    file(it.tryGetScalarNode("file")?.asAbsolutePath()).adjustTrace(it.tryGetScalarNode("file"))
                    usernameKey(it.tryGetScalarNode("usernameKey")) { /* TODO report */ }
                    passwordKey(it.tryGetScalarNode("passwordKey")) { /* TODO report */ }
                }.adjustTrace(it)
            }
        )
    }

    else -> null
}?.adjustTrace(this)

context(ConvertCtx, ProblemReporterContext)
private fun Node.convertDependencies() = when (this) {
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
            value.startsWith(".") -> value?.let { InternalDependency().apply { path(it.asAbsolutePath()).adjustTrace(node)  } }
            // TODO Report non existent path.
            value.startsWith("$") -> value
                ?.let { CatalogDependency().apply { catalogKey(it.removePrefix("$")).adjustTrace(node) } }
            else -> value?.let { ExternalMavenDependency().apply { coordinates(node as ScalarNode) } }
        }
    } else {
        when {
            this is MappingNode && value.size > 1 -> TODO("report")
            this is MappingNode && value.isEmpty() -> TODO("report")
            this is MappingNode && value.first().keyNode.asScalarNode()?.value?.startsWith("$") == true ->
                value.first().convertCatalogDep()
            this is MappingNode && value.first().keyNode.asScalarNode()?.value?.startsWith(".") == true ->
                value.first().convertInternalDep()
            this is MappingNode && value.first().keyNode.asScalarNode()?.value != null ->
                value.first().convertExternalMavenDep()
            else -> null // Report wrong type
        }
    }?.adjustTrace(this)
}

context(ConvertCtx, ProblemReporterContext)
private fun NodeTuple.convertExternalMavenDep() = ExternalMavenDependency().apply {
    coordinates(keyNode.asScalarNode(true))
    convertScopes(this)
}

context(ConvertCtx, ProblemReporterContext)
private fun NodeTuple.convertInternalDep(): InternalDependency = InternalDependency().apply {
    path(keyNode.asScalarNode(true)!!.asAbsolutePath()).adjustTrace(keyNode)
    convertScopes(this)
}

context(ConvertCtx, ProblemReporterContext)
private fun NodeTuple.convertCatalogDep(): CatalogDependency = CatalogDependency().apply {
    catalogKey(keyNode.asScalarNode(true)?.value?.removePrefix("$")).adjustTrace(keyNode)
    convertScopes(this)
}

context(ConvertCtx, ProblemReporterContext)
private fun NodeTuple.convertScopes(dep: Dependency) = with(dep) {
    val valueNode = valueNode
    when {
        valueNode is ScalarNode && valueNode.value == "exported" -> exported(true).adjustTrace(valueNode)
        valueNode is ScalarNode -> scope(valueNode, DependencyScope)
        valueNode is MappingNode -> {
            scope(valueNode.tryGetScalarNode("scope"), DependencyScope)
            exported(valueNode.tryGetScalarNode("exported"))
        }

        else -> error("Unreachable")
    }
}

fun <T : Traceable> T.adjustTrace(it: Node?) = apply { trace = it }