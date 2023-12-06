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
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.reader

context(ProblemReporterContext)
fun convertModule(file: Path): Module {
    val yaml = Yaml()
    val rootNode = yaml.compose(file.reader())
    // TODO Add reporting.
    if (rootNode !is MappingNode) return Module()
    return rootNode.convertModule()
}

context(ProblemReporterContext)
fun convertTemplate(file: Path): Template {
    val yaml = Yaml()
    val rootNode = yaml.compose(file.reader())
    if (rootNode !is MappingNode) return Template()
    return rootNode.convertBase(Template())
}

context(ProblemReporterContext)
private fun MappingNode.convertModule() = Module().apply {
    product(tryGetMappingNode("product")?.convertProduct()) { /* TODO report */ }
    apply(tryGetScalarSequenceNode("apply")?.map { Path(it.value) /* TODO check path */ })
    alias(tryGetMappingNode("alias")?.convertScalarKeyedMap {
        // TODO Report non enum value.
        asScalarSequenceNode()?.mapNotNull { it.convertEnum(Platform.entries) }?.toSet()
    })
    convertBase(this)
}

context(ProblemReporterContext)
private fun <T : Base> MappingNode.convertBase(base: T) = base.apply {
    repositories(tryGetMappingNode("repositories")?.convertRepositories())
    dependencies(convertWithModifiers("dependencies") { convertDependencies() })
    settings(convertWithModifiers("settings") { convertSettings() })
    `test-dependencies`(convertWithModifiers("test-dependencies") { convertDependencies() })
    `test-settings`(convertWithModifiers("test-settings") { convertSettings() })
}

context(ProblemReporterContext)
private fun MappingNode.convertProduct() = ModuleProduct().apply {
    type(tryGetScalarNode("type")?.convertEnum(ProductType.entries)) { /* TODO report */ }
    platforms(tryGetScalarSequenceNode("platforms")
        ?.mapNotNull { it.convertEnum(Platform.entries) /* TODO report */ }
    )
}

context(ProblemReporterContext)
private fun Node.convertRepositories(): Repositories? {
    if (this@convertRepositories !is SequenceNode) return null
    // TODO Report wrong type.
    val repos = value.mapNotNull { it.convertRepository() }
    return Repositories().apply { repositories(repos) }
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
                    file(tryGetScalarNode("file")?.value?.let { Path(it) }) { /* TODO report */ }
                    usernameKey(tryGetScalarNode("usernameKey")?.value) { /* TODO report */ }
                    passwordKey(tryGetScalarNode("passwordKey")?.value) { /* TODO report */ }
                }
            }
        )
    }

    else -> null
}

context(ProblemReporterContext)
private fun Node.convertDependencies() = assertNodeType<SequenceNode, Dependencies>("dependencies") {
    val dependencies = value.mapNotNull { it.convertDependency() }
    return Dependencies().apply { deps(dependencies) }
}

context(ProblemReporterContext)
private fun Node.convertDependency(): Dependency? = when {
    this is ScalarNode && value.startsWith(".") ->
        // TODO Report non existent path.
        value?.let { InternalDependency().apply { path(Path(it)) } }
    this is ScalarNode ->
        value?.let { ExternalMavenDependency().apply { coordinates(it) } }
    else -> null // Report wrong type
}