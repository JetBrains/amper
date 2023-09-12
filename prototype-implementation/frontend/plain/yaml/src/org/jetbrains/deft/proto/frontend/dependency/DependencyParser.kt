package org.jetbrains.deft.proto.frontend.dependency

import org.jetbrains.deft.proto.frontend.*

private data class NotationWithFlags(
    val notation: String,
    val runtime: Boolean,
    val compile: Boolean,
    val exported: Boolean
)

private val stringDependencyFormat: (Any) -> NotationWithFlags? = { dependency ->
    (dependency as? String)?.let { NotationWithFlags(it, runtime = true, compile = true, exported = false) }
}

private val inlineDependencyFormat: (Any) -> NotationWithFlags? = { dependency ->
    @Suppress("UNCHECKED_CAST")
    (dependency as? Settings)?.let {
        if (it.size != 1) {
            return@let null
        }
        val entry = it.entries.first()
        val notation = entry.key
        (entry.value as? String)?.let {
            val (compile, runtime, exported) = when (it) {
                "compile-only" -> Triple(true, false, false)
                "runtime-only" -> Triple(false, true, false)
                "all" -> Triple(true, true, false)
                "exported" -> Triple(true, true, true)
                else -> Triple(true, true, false)
            }
            NotationWithFlags(notation, compile, runtime, exported)
        }
    }
}

private val fullDependencyFormat: (Any) -> NotationWithFlags? = { dependency ->
    @Suppress("UNCHECKED_CAST")
    (dependency as? Settings)?.let {
        if (it.size != 1) {
            return@let null
        }
        val entry = it.entries.first()
        val notation = entry.key

        (entry.value as? Settings)?.let {
            val scope = it.getValue<String>("scope") ?: "all"
            val (compile, runtime) = when (scope) {
                "compile-only" -> true to false
                "runtime-only" -> false to true
                "all" -> true to true
                else -> true to true
            }
            val exported = it.getValue<Boolean>("exported") ?: false
            NotationWithFlags(notation, compile, runtime, exported)
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
fun parseDependency(dependency: Any): Notation? {
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