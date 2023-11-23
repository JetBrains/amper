/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.dependency.resolution

import java.util.*

fun Resolver.Companion.createFor(rootProvider: (Resolver) -> DependencyNode, block: Builder.() -> Unit = {}): Resolver =
    Builder(rootProvider).apply(block).build()

class Resolver(rootProvider: (Resolver) -> DependencyNode, val settings: Settings) {

    companion object;

    internal val cache = ResolutionCache()
    val root: DependencyNode = rootProvider(this)

    fun buildGraph(level: ResolutionLevel = ResolutionLevel.FULL): Resolver {
        val queue = LinkedList(listOf(root))
        while (queue.isNotEmpty()) {
            val node = queue.remove()
            if (node.level < level) {
                if (node.state < level.state) {
                    node.resolve(level)
                }
                node.level = level
                queue.addAll(node.children)
            }
        }
        return this
    }

    fun downloadDependencies(): Resolver {
        val queue = LinkedList(listOf(root))
        while (queue.isNotEmpty()) {
            val node = queue.remove()
            node.downloadDependencies()
            queue.addAll(node.children)
        }
        return this
    }
}

interface DependencyNode {

    var state: ResolutionState
    var level: ResolutionLevel
    val children: Collection<DependencyNode>
    val messages: Collection<Message>

    fun resolve(level: ResolutionLevel)
    fun downloadDependencies()

    fun asSequence(): Sequence<DependencyNode> = sequence {
        val queue = LinkedList(listOf(this@DependencyNode))
        while (queue.isNotEmpty()) {
            val node = queue.remove()
            yield(node)
            queue.addAll(node.children)
        }
    }

    fun prettyPrint(): String = buildString { prettyPrint(this) }

    private fun prettyPrint(
        builder: StringBuilder,
        indent: StringBuilder = StringBuilder(),
        visited: MutableSet<DependencyNode> = mutableSetOf(),
        addLevel: Boolean = false,
    ) {
        builder.append(indent).append(toString())

        val seen = !visited.add(this)
        if (seen && children.isNotEmpty()) {
            builder.append(" (*)")
        }
        builder.append('\n')
        if (seen || children.isEmpty()) {
            return
        }

        if (indent.isNotEmpty()) {
            indent.setLength(indent.length - 5)
            if (addLevel) {
                indent.append("|    ")
            } else {
                indent.append("     ")
            }
        }

        children.forEachIndexed { i, it ->
            val addLevel = i < children.size - 1
            if (addLevel) {
                indent.append("+--- ")
            } else {
                indent.append("\\--- ")
            }
            it.prettyPrint(builder, indent, visited, addLevel)
            indent.setLength(indent.length - 5)
        }
    }
}

enum class ResolutionState {
    UNKNOWN, UNSURE, RESOLVED
}

enum class ResolutionLevel(val state: ResolutionState) {
    CREATED(ResolutionState.UNKNOWN),
    PARTIAL(ResolutionState.UNSURE),
    FULL(ResolutionState.RESOLVED),
}

class Progress

enum class Scope {
    COMPILE, RUNTIME
}

class AmperDependencyResolutionException(message: String) : RuntimeException(message)

class Builder(val rootProvider: (Resolver) -> DependencyNode) {

    var progress: Progress = Progress()
    var scope: Scope = Scope.COMPILE
    var platform: String = "jvm"
    var repositories: Collection<String> = listOf("https://repo1.maven.org/maven2")
    var cache: List<CacheDirectory> = listOf(GradleCacheDirectory(), MavenCacheDirectory())

    val settings: Settings
        get() = Settings(
            progress,
            scope,
            platform,
            repositories,
            cache,
        )

    fun build(): Resolver = Resolver(rootProvider, settings)
}

data class Settings(
    val progress: Progress,
    val scope: Scope,
    val platform: String,
    val repositories: Collection<String>,
    val fileCache: List<CacheDirectory>,
)

data class Message(
    val text: String,
    val extra: String = "",
    val severity: Severity = Severity.INFO,
)

enum class Severity {
    INFO, WARNING, ERROR
}
