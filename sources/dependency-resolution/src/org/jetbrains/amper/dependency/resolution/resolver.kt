/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.dependency.resolution

import org.jetbrains.amper.dependency.resolution.ResolutionLevel.LOCAL
import org.jetbrains.amper.dependency.resolution.ResolutionState.UNSURE
import java.util.*

/**
 * This is the entry point to the library.
 *
 * The suggested usage as is follows.
 *
 * 1. Create a resolver instance providing an initial graph of nodes.
 * 2. Build a dependency graph, optionally providing a desired [ResolutionLevel].
 * 3. Optionally, rebuild the graph by providing a higher [ResolutionLevel].
 * 4. Traverse the graph manually or [downloadDependencies] at once.
 *
 * This is how one can download 'kotlin-test' and all its JVM dependencies from Maven Central.
 *
 * ```Kotlin
 * Resolver(
 *     MavenDependencyNode(
 *         Context {
 *             scope = Scope.COMPILE
 *             platform = "jvm"
 *         },
 *         "org.jetbrains.kotlin", "kotlin-test", "1.9.20"
 *     )
 * ).buildGraph().downloadDependencies().root
 * ```
 *
 * @see ModuleDependencyNode
 * @see MavenDependencyNode
 * @see Context
 */
class Resolver(val root: DependencyNode) {

    /**
     * Builds a dependency graph starting from [root].
     * Can be called multiple times on the same instance to perform resolution with or without network access.
     * One can get a faster result with [ResolutionLevel.LOCAL],
     * but it might be incomplete if not all previous dependencies were downloaded.
     *
     * @param level whether resolution should be performed with (default) or without network access
     * @return current instance
     */
    fun buildGraph(level: ResolutionLevel = ResolutionLevel.NETWORK): Resolver {
        val nodes = mutableMapOf<Key<*>, MutableList<DependencyNode>>()
        val conflicts = mutableSetOf<Key<*>>()
        val queue = LinkedList(listOf(root))
        do {
            conflicts.clear()
            // 1. Process queue tracking nodes and conflicts.
            while (queue.isNotEmpty()) {
                val node = queue.remove()
                // 1.1. Check if a node with such a key was already registered.
                val candidates = nodes.computeIfAbsent(node.key) { mutableListOf() }.also { it += node }
                // 1.2. If it's a known or new conflict, postpone node processing for later.
                if (node.key in conflicts || candidates.haveConflicts()) {
                    conflicts += node.key
                    continue
                }
                // 1.3. If the node hasn't yet reached a state corresponding to the desired level, resolve it.
                if (node.state < level.state) {
                    node.resolve(level)
                }
                // 1.4. Process the node's children.
                queue.addAll(node.children)
            }
            // 2. Process nodes with conflicts.
            for (key in conflicts) {
                val candidates = nodes[key] ?: throw AmperDependencyResolutionException("Nodes are missing for $key")
                // 2.1. Try resolving conflicts using a provided strategy and finish if impossible to achieve.
                if (!candidates.resolveConflict()) {
                    return this
                }
                // 2.2. Continue processing nodes as it was postponed.
                queue.addAll(candidates)
            }
            // 3. Repeat the cycle until there are no conflicts.
        } while (conflicts.isNotEmpty())
        return this
    }

    private fun List<DependencyNode>.haveConflicts() =
        root.context.settings.conflictResolutionStrategies
            .filter { it.isApplicableFor(this) }
            .any { it.seesConflictsIn(this) }

    private fun List<DependencyNode>.resolveConflict() =
        root.context.settings.conflictResolutionStrategies
            .filter { it.isApplicableFor(this) }
            .find { it.seesConflictsIn(this) }
            ?.resolveConflictsIn(this) ?: true

    /**
     * Downloads dependencies of all nodes by traversing a dependency graph.
     */
    fun downloadDependencies(): Resolver = root.asSequence().forEach { it.downloadDependencies() }.let { this }
}

/**
 * A dependency node is a graph element.
 *
 * It has the following preporties.
 *
 * - Holds a context relevant for it and its children.
 * - Can be compared by a [Key] that's the same for all nodes with equal coordinates but different versions.
 * - Has mutable state, children, and messages that could change as a result of the resolution process.
 *
 * By the resolution process we mean finding the node's dependencies (children) according to provided context,
 * namely, a [ResolutionScope] and a platform.
 */
interface DependencyNode {

    val context: Context
    val key: Key<*>
    var state: ResolutionState
    val children: List<DependencyNode>
    val messages: List<Message>

    /**
     * Fills [children] and changes [state] taking [context] into account.
     * In the process, [messages] are populated with relevant information or errors.
     *
     * @see ResolutionState
     * @see Message
     * @see Severity
     */
    fun resolve(level: ResolutionLevel)

    /**
     * Ensures that the dependency-relevant files are on disk according to settings.
     */
    fun downloadDependencies()

    /**
     * Returns a sequence of nodes below the current one using BFS.
     */
    fun asSequence(): Sequence<DependencyNode> = sequence {
        val queue = LinkedList(listOf(this@DependencyNode))
        while (queue.isNotEmpty()) {
            val node = queue.remove()
            yield(node)
            queue.addAll(node.children)
        }
    }

    /**
     * Prints the graph below the node using a Gradle-like output style.
     */
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
            val addAnotherLevel = i < children.size - 1
            if (addAnotherLevel) {
                indent.append("+--- ")
            } else {
                indent.append("\\--- ")
            }
            it.prettyPrint(builder, indent, visited, addAnotherLevel)
            indent.setLength(indent.length - 5)
        }
    }
}

/**
 * Describes a state of the dependency resolution process.
 * It can be [UNSURE] if the resolution was done without network access.
 *
 * @see ResolutionLevel
 */
enum class ResolutionState {
    INITIAL, UNSURE, RESOLVED
}

/**
 * Describes a level of the dependency resolution process.
 * [LOCAL] assumes that no network access is performed.
 * It gives a result faster, but it might be incomplete if not all dependencies were downloaded before.
 *
 * @property state minimal expected resolution result for the corresponding level
 * @see ResolutionState
 */
enum class ResolutionLevel(val state: ResolutionState) {
    LOCAL(ResolutionState.UNSURE),
    NETWORK(ResolutionState.RESOLVED),
}

class Progress

class AmperDependencyResolutionException(message: String) : RuntimeException(message)
