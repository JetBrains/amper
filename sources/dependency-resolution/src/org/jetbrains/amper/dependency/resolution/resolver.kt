/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.amper.dependency.resolution.ResolutionLevel.LOCAL
import org.jetbrains.amper.dependency.resolution.ResolutionState.UNSURE
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

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
    suspend fun buildGraph(level: ResolutionLevel = ResolutionLevel.NETWORK) {
        val nodes = ConcurrentHashMap<Key<*>, MutableList<DependencyNode>>()
        val conflicts = ConcurrentHashMap.newKeySet<Key<*>>()
        val queue = ConcurrentLinkedQueue(listOf(root))
        do {
            conflicts.clear()
            coroutineScope {
                queue.forEach { node ->
                    node.launchRecursiveResolution(level, nodes, conflicts)
                }
            }

            queue.clear()
            coroutineScope {
                conflicts.forEach { key ->
                    launch {
                        val candidates = nodes[key]
                            ?: throw AmperDependencyResolutionException("Nodes are missing for $key")
                        if (!candidates.resolveConflict()) {
                            return@launch
                        }
                        queue.addAll(candidates)
                    }
                }
            }
        } while (conflicts.isNotEmpty())
    }

    /**
     * Launches a new coroutine to resolve this [DependencyNode], keeping track of the resolution job in the node cache.
     */
    context(CoroutineScope)
    private fun DependencyNode.launchRecursiveResolution(
        level: ResolutionLevel,
        nodes: MutableMap<Key<*>, MutableList<DependencyNode>>,
        conflicts: MutableSet<Key<*>>,
    ) {
        context.nodeCache[resolutionJobKey] = launch {
            if (key !in conflicts) {
                resolveRecursively(level, nodes, conflicts)
            }
        }.apply {
            // This ensures the job is removed from the map upon completion even in cases where it is cancelled
            // before it even starts. We can't achieve this with a try-finally inside the launch.
            invokeOnCompletion {
                context.nodeCache.remove(resolutionJobKey)
            }
        }
    }

    private suspend fun DependencyNode.resolveRecursively(
        level: ResolutionLevel,
        nodes: MutableMap<Key<*>, MutableList<DependencyNode>>,
        conflicts: MutableSet<Key<*>>,
    ) {
        coroutineScope {
            resolveChildren(level)
            children.forEach { node ->
                val similarNodes = nodes.computeIfAbsent(node.key) { CopyOnWriteArrayList() }.also { it += node }
                if (node.key !in conflicts) {
                    if (similarNodes.firstOrNull()?.conflictsWith(node) == true) {
                        conflicts += node.key
                        similarNodes.forEach { it.context.nodeCache[resolutionJobKey]?.cancel() }
                    } else {
                        node.launchRecursiveResolution(level, nodes, conflicts)
                    }
                }
            }
        }
    }

    private val resolutionJobKey = Key<Job>("resolutionJob")

    private fun DependencyNode.conflictsWith(other: DependencyNode) =
        root.context.settings.conflictResolutionStrategies
            .filter { it.isApplicableFor(listOf(this, other)) }
            .any { it.seesConflictsIn(listOf(this, other)) }

    private fun List<DependencyNode>.resolveConflict() =
        root.context.settings.conflictResolutionStrategies
            .filter { it.isApplicableFor(this) }
            .find { it.seesConflictsIn(this) }
            ?.resolveConflictsIn(this) ?: true

    /**
     * Downloads dependencies of all nodes by traversing a dependency graph.
     */
    suspend fun downloadDependencies() = root.asSequence().distinctBy { it.key }.forEach { it.downloadDependencies() }
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

    val parent: DependencyNode? get() = context.nodeCache[parentNodeKey]
    val context: Context
    val key: Key<*>
    val children: List<DependencyNode>
    val messages: List<Message>

    /**
     * Fills [children] taking [context] into account.
     * In the process, [messages] are populated with relevant information or errors.
     *
     * @see ResolutionState
     * @see Message
     * @see Severity
     */
    suspend fun resolveChildren(level: ResolutionLevel)

    /**
     * Ensures that the dependency-relevant files are on disk according to settings.
     */
    suspend fun downloadDependencies()

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
