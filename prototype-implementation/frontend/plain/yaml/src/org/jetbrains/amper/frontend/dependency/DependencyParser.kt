package org.jetbrains.amper.frontend.dependency

import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.asDeftSuccess
import org.jetbrains.amper.core.flatMap
import org.jetbrains.amper.core.map
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.*
import org.jetbrains.amper.frontend.nodes.YamlNode
import org.jetbrains.amper.frontend.nodes.getBooleanValue
import org.jetbrains.amper.frontend.nodes.getStringValue

private data class NotationWithFlags(
    val notation: String,
    val runtime: Boolean,
    val compile: Boolean,
    val exported: Boolean,
    val node: YamlNode,
)

context(ProblemReporterContext)
fun Catalog.replaceIfInCatalogue(notation: String, node: YamlNode): Result<String> =
    if (notation.startsWith("$")) {
        findInCatalogue(notation.removePrefix("$"), node)
    } else
        Result.success(notation)

context(ProblemReporterContext)
private fun stringDependencyFormat(): Catalog.(YamlNode) -> Result<NotationWithFlags>? = { dependency ->
    (dependency as? YamlNode.Scalar)?.let {
        replaceIfInCatalogue(dependency.value, dependency).map {
            NotationWithFlags(it, runtime = true, compile = true, exported = false, dependency)
        }
    }
}

context(ProblemReporterContext)
private fun inlineDependencyFormat(): Catalog.(YamlNode) -> Result<NotationWithFlags>? = { dependency ->
    (dependency as? YamlNode.Mapping)?.let {
        if (dependency.size != 1) {
            return@let null
        }
        val entry = dependency.mappings.first()
        val notation = replaceIfInCatalogue((entry.first as YamlNode.Scalar).value, entry.first)
        (entry.second as? YamlNode.Scalar)?.let {
            val (compile, runtime, exported) = when (it.value) {
                "compile-only" -> Triple(true, false, false)
                "runtime-only" -> Triple(false, true, false)
                "all" -> Triple(true, true, false)
                "exported" -> Triple(true, true, true)
                else -> Triple(true, true, false)
            }
            notation.map {
                NotationWithFlags(it, compile, runtime, exported, entry.first)
            }
        }
    }
}

context(ProblemReporterContext)
private fun fullDependencyFormat(): Catalog.(YamlNode) -> Result<NotationWithFlags>? = { dependency ->
    (dependency as? YamlNode.Mapping)?.let {
        if (dependency.size != 1) {
            return@let null
        }
        val entry = dependency.mappings.first()
        val notation = replaceIfInCatalogue((entry.first as YamlNode.Scalar).value, entry.first)

        (entry.second as? YamlNode.Mapping)?.let { dependencySettings ->
            val scope = dependencySettings.getStringValue("scope") ?: "all"
            val (compile, runtime) = when (scope) {
                "compile-only" -> true to false
                "runtime-only" -> false to true
                "all" -> true to true
                else -> true to true
            }
            val exported = dependencySettings.getBooleanValue("exported") ?: false
            notation.map {
                NotationWithFlags(it, compile, runtime, exported, entry.first)
            }
        }
    }
}


private val internalNotationFormat: (NotationWithFlags) -> ((BuildFileAware) -> Result<Notation>)? =
    { notationWithFlags ->
        if (notationWithFlags.notation.startsWith(".") || notationWithFlags.notation.startsWith("/")) {
            { context ->
                with(context) {
                    DefaultPotatoModuleDependency(
                        notationWithFlags.notation,
                        notationWithFlags.compile,
                        notationWithFlags.runtime,
                        notationWithFlags.exported,
                        notationWithFlags.node,
                    ).asDeftSuccess()
                }
            }
        } else {
            null
        }
    }
private val externalNotationFormat: (NotationWithFlags) -> ((BuildFileAware) -> Result<Notation>)? =
    { notationWithFlags ->
        if (!notationWithFlags.notation.startsWith(".") && !notationWithFlags.notation.startsWith("/")) {
            {
                MavenDependency(
                    notationWithFlags.notation,
                    notationWithFlags.compile,
                    notationWithFlags.runtime,
                    notationWithFlags.exported
                ).asDeftSuccess()
            }
        } else {
            null
        }
    }

context(BuildFileAware, ProblemReporterContext)
fun Catalog.parseDependency(dependency: YamlNode): Result<Notation>? {
    val dependencyFormats = listOf(
        stringDependencyFormat(),
        inlineDependencyFormat(),
        fullDependencyFormat(),
    )
    val notationFormats: List<(NotationWithFlags) -> ((BuildFileAware) -> Result<Notation>)?> = listOf(
        internalNotationFormat,
        externalNotationFormat
    )
    return dependencyFormats
        .firstNotNullOfOrNull { it(dependency) }
        ?.flatMap { dep ->
            val parsed = notationFormats.firstNotNullOfOrNull { it(dep) }
            parsed?.invoke(this@BuildFileAware)
        }
}
