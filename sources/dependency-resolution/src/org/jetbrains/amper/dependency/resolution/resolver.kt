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
        // Contains all nodes that we resolved (we populated the children of their internal dependency), and for which
        // all child nodes resolutions have either completed or been cancelled. The cancelled descendant nodes are
        // tracked by the conflict resolution structures, so they won't be forgotten anyway, but they don't require a
        // new resolution of their ancestors. This is why it's ok to mark a parent as resolved in that case.
        val resolvedNodes = ConcurrentHashMap.newKeySet<DependencyNode>()
        do {
            conflicts.clear()
            coroutineScope {
                queue.forEach { node ->
                    node.launchRecursiveResolution(level, nodes, conflicts, resolvedNodes)
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

                        // Some candidates may have been resolved entirely before the conflict was detected,
                        // so we need to "unmark" them as resolved because their dependency may have changed
                        // after conflict resolution (requiring a new resolution).
                        resolvedNodes.removeAll(candidates)
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
        resolvedNodes: MutableSet<DependencyNode>,
    ) {
        // If a conflict causes the cancellation of all jobs, and this cancellation happens between the moment when the new
        // job is launched and the moment it is registered for tracking, we could miss the cancellation.
        // This is ok because the launched job will then check for conflicts and end almost immediately.
        val job = launch {
            resolveRecursively(level, nodes, conflicts, resolvedNodes)
        }
        // This ensures the job is removed from the list upon completion even in cases where it is cancelled
        // before it even starts. We can't achieve this with a try-finally inside the coroutine itself.
        job.invokeOnCompletion {
            resolutionJobs.remove(job)
        }
        resolutionJobs.add(job)
    }

    private suspend fun DependencyNode.resolveRecursively(
        level: ResolutionLevel,
        nodes: MutableMap<Key<*>, MutableList<DependencyNode>>,
        conflicts: MutableSet<Key<*>>,
        resolvedNodes: MutableSet<DependencyNode>,
    ) {
        if (this in resolvedNodes) {
            return // skipping already resolved node
        }

        val similarNodes = nodes.computeIfAbsent(key) { CopyOnWriteArrayList() }
        similarNodes.add(this) // register for potential future conflict resolution
        if (key in conflicts) {
            return // we don't want to resolve conflicted candidates in this wave
        }
        if (similarNodes.size > 1 && similarNodes.any { it !== this && it.conflictsWith(this) }) {
            conflicts += key
            similarNodes.forEach { it.resolutionJobs.forEach(Job::cancel) }
            return // we don't want to resolve conflicted candidates in this wave
        }
        resolveChildren(level)
        coroutineScope {
            children.forEach { node ->
                node.launchRecursiveResolution(level, nodes, conflicts, resolvedNodes)
            }
        }
        resolvedNodes.add(this)
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
    suspend fun downloadDependencies() = root.distinctBfsSequence().distinctBy { it.key }.forEach { it.downloadDependencies() }
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

    val parents: List<DependencyNode> get() = context.nodeParents
    val context: Context
    val key: Key<*>
    val children: List<DependencyNode>
    val messages: List<Message>

    /**
     * Fills [children], taking [context] into account.
     * In the process, [messages] are populated with relevant information or errors.
     *
     * Does nothing if this node was already resolved to at least the same [level].
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
     * Returns a sequence of distinct nodes using BFS starting at (and including) this node.
     *
     * The nodes are distinct in terms of referential identity, which is enough to eliminate duplicate "requested"
     * dependency triplets. This does NOT eliminate nodes that requested the same dependency in different versions,
     * even though conflict resolution should make them point to the same dependency version internally eventually.
     *
     * This sequence is guaranteed to be finite, as it prunes the graph when encountering duplicates (and thus cycles).
     */
    fun distinctBfsSequence(): Sequence<DependencyNode> = sequence {
        val queue = LinkedList(listOf(this@DependencyNode))
        val visited = mutableSetOf<DependencyNode>()
        while (queue.isNotEmpty()) {
            val node = queue.remove()
            yield(node)
            visited.add(node)
            queue.addAll(node.children.filter { it !in visited })
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
 * The thread-safe list of coroutine [Job]s currently resolving this node.
 *
 * We use a copy-on-write array list here, so that we can iterate it safely for cancellation despite the fact that the
 * cancellation itself ultimately modifies the list (removes terminated jobs).
 */
// TODO this should probably be an internal property of the dependency node instead of being stored in the nodeCache
private val DependencyNode.resolutionJobs: MutableList<Job>
    get() = context.nodeCache.computeIfAbsent(Key<MutableList<Job>>("resolutionJobs")) { CopyOnWriteArrayList() }

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
