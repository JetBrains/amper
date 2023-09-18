package org.jetbrains.deft.proto.frontend.dependency

import org.jetbrains.deft.proto.frontend.BuildFileAware
import org.jetbrains.deft.proto.frontend.DefaultPotatoModuleDependency
import org.jetbrains.deft.proto.frontend.MavenDependency
import org.jetbrains.deft.proto.frontend.Notation
import org.jetbrains.deft.proto.frontend.nodes.YamlNode
import org.jetbrains.deft.proto.frontend.nodes.getBooleanValue
import org.jetbrains.deft.proto.frontend.nodes.getStringValue

private data class NotationWithFlags(
    val notation: String,
    val runtime: Boolean,
    val compile: Boolean,
    val exported: Boolean
)

private val stringDependencyFormat: (YamlNode) -> NotationWithFlags? = { dependency ->
    (dependency as? YamlNode.Scalar)?.let { NotationWithFlags(dependency.value, runtime = true, compile = true, exported = false) }
}

private val inlineDependencyFormat: (YamlNode) -> NotationWithFlags? = { dependency ->
    (dependency as? YamlNode.Mapping)?.let {
        if (dependency.size != 1) {
            return@let null
        }
        val entry = dependency.mappings.first()
        val notation = entry.first as YamlNode.Scalar
        (entry.second as? YamlNode.Scalar)?.let {
            val (compile, runtime, exported) = when (it.value) {
                "compile-only" -> Triple(true, false, false)
                "runtime-only" -> Triple(false, true, false)
                "all" -> Triple(true, true, false)
                "exported" -> Triple(true, true, true)
                else -> Triple(true, true, false)
            }
            NotationWithFlags(notation.value, compile, runtime, exported)
        }
    }
}

private val fullDependencyFormat: (YamlNode) -> NotationWithFlags? = { dependency ->
    (dependency as? YamlNode.Mapping)?.let {
        if (dependency.size != 1) {
            return@let null
        }
        val entry = dependency.mappings.first()
        val notation = entry.first as YamlNode.Scalar

        (entry.second as? YamlNode.Mapping)?.let { dependencySettings ->
            val scope = dependencySettings.getStringValue("scope") ?: "all"
            val (compile, runtime) = when (scope) {
                "compile-only" -> true to false
                "runtime-only" -> false to true
                "all" -> true to true
                else -> true to true
            }
            val exported = dependencySettings.getBooleanValue("exported") ?: false
            NotationWithFlags(notation.value, compile, runtime, exported)
        }
    }
}


private val internalNotationFormat: (NotationWithFlags) -> ((BuildFileAware) -> Notation)? = { notationWithFlags ->
    if (notationWithFlags.notation.startsWith(".") || notationWithFlags.notation.startsWith("/")) {
        { context ->
            with(context) {
                DefaultPotatoModuleDependency(
                    notationWithFlags.notation,
                    notationWithFlags.compile,
                    notationWithFlags.runtime,
                    notationWithFlags.exported
                )
            }
        }
    } else {
        null
    }
}
private val externalNotationFormat: (NotationWithFlags) -> ((BuildFileAware) -> Notation)? = { notationWithFlags ->
    if (!notationWithFlags.notation.startsWith(".") && !notationWithFlags.notation.startsWith("/")) {
        {
            MavenDependency(
                notationWithFlags.notation,
                notationWithFlags.compile,
                notationWithFlags.runtime,
                notationWithFlags.exported
            )
        }
    } else {
        null
    }
}

private infix fun <A, B, C> List<(A) -> B?>.combine(functions: List<(B) -> C?>): (A) -> C? = { value ->
    functions.firstNotNullOf { it(firstNotNullOf { it(value) }) }
}

context(BuildFileAware)
fun parseDependency(dependency: YamlNode): Notation? {
    val dependencyFormats = listOf(
        stringDependencyFormat,
        inlineDependencyFormat,
        fullDependencyFormat
    )
    val notationFormats: List<(NotationWithFlags) -> ((BuildFileAware) -> Notation)?> = listOf(
        internalNotationFormat,
        externalNotationFormat
    )
    val resultingFunction = (dependencyFormats combine notationFormats)(dependency)
    return resultingFunction?.let {
        it(this@BuildFileAware)
    }
}
