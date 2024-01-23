/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.dependency.resolution

import java.nio.file.Path
import java.util.*

class Resolver(val root: DependencyNode) {

    fun buildGraph(level: ResolutionLevel = ResolutionLevel.NETWORK): Resolver {
        val nodes = mutableMapOf<Key<*>, LinkedHashSet<DependencyNode>>()
        val conflicts = mutableSetOf<Key<*>>()
        val queue = LinkedList(listOf(root))
        do {
            conflicts.clear()
            while (queue.isNotEmpty()) {
                val node = queue.remove()
                val candidates = nodes.computeIfAbsent(node.key) { LinkedHashSet() }.also { it += node }
                if (node.key in conflicts || candidates.haveConflicts()) {
                    conflicts += node.key
                    continue
                }
                if (node.state < level.state) {
                    node.resolve(level)
                }
                node.level = level
                queue.addAll(node.children)
            }
            for (key in conflicts) {
                val candidates = nodes[key] ?: throw AmperDependencyResolutionException("Nodes are missing for $key")
                candidates.resolveConflict()
                queue.addAll(candidates)
            }
        } while (conflicts.isNotEmpty())
        return this
    }

    private fun LinkedHashSet<DependencyNode>.haveConflicts() =
        root.context.settings.conflictResolutionStrategies
            .filter { it.isApplicableFor(this) }
            .any { it.seesConflictsIn(this) }

    private fun LinkedHashSet<DependencyNode>.resolveConflict() {
        root.context.settings.conflictResolutionStrategies
            .filter { it.isApplicableFor(this) }
            .find { it.seesConflictsIn(this) }
            ?.resolveConflictsIn(this)
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

    val context: Context
    val key: Key<*>
    var state: ResolutionState
    var level: ResolutionLevel
    val children: List<DependencyNode>
    val messages: List<Message>

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
        visited: MutableSet<Key<*>> = mutableSetOf(),
        addLevel: Boolean = false,
    ) {
        builder.append(indent).append(toString())

        val seen = !visited.add(key)
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
    LOCAL(ResolutionState.UNSURE),
    NETWORK(ResolutionState.RESOLVED),
}

class Progress

class AmperDependencyResolutionException(message: String) : RuntimeException(message)

class Context(val settings: Settings) {

    val cache: ResolutionCache = ResolutionCache()

    companion object {
        fun build(block: Builder.() -> Unit = {}): Context = Builder(block).build()
    }
}

class Builder(init: Builder.() -> Unit = {}) {

    var progress: Progress = Progress()
    var scope: Scope = Scope.COMPILE
    var platform: String = "jvm"
    var repositories: List<String> = listOf("https://repo1.maven.org/maven2")
    var cache: FileCacheBuilder.() -> Unit = {}
    var conflictResolutionStrategies = listOf(HighestVersionStrategy())

    init {
        apply(init)
    }

    val settings: Settings
        get() = Settings(
            progress,
            scope,
            platform,
            repositories,
            FileCacheBuilder(cache).build(),
            conflictResolutionStrategies,
        )

    fun build(): Context = Context(settings)
}

class FileCacheBuilder(init: FileCacheBuilder.() -> Unit = {}) {

    var amperCache: Path = Path.of(System.getProperty("user.home"), ".amper")
    var localRepositories: List<LocalRepository> = listOf(GradleLocalRepository(), MavenLocalRepository())
    var fallbackLocalRepository: LocalRepository? = null

    init {
        apply(init)
    }

    fun build(): FileCache = FileCache(
        amperCache, localRepositories, fallbackLocalRepository ?: localRepositories.first()
    )
}

data class Settings(
    val progress: Progress,
    val scope: Scope,
    val platform: String,
    val repositories: List<String>,
    val fileCache: FileCache,
    val conflictResolutionStrategies: List<ConflictResolutionStrategy>,
)

data class FileCache(
    val amperCache: Path,
    val localRepositories: List<LocalRepository>,
    val fallbackLocalRepository: LocalRepository,
)

data class Message(
    val text: String,
    val extra: String = "",
    val severity: Severity = Severity.INFO,
)

enum class Severity {
    INFO, WARNING, ERROR
}
