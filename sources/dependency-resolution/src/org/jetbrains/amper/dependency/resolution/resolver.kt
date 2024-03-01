/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.amper.concurrency.StripedMutex
import org.jetbrains.amper.dependency.resolution.ResolutionLevel.LOCAL
import org.jetbrains.amper.dependency.resolution.ResolutionState.UNSURE
import java.util.*
import java.util.concurrent.ConcurrentHashMap
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
        val conflictResolver = ConflictResolver(root.context.settings.conflictResolutionStrategies)

        // Contains all nodes that we resolved (we populated the children of their internal dependency), and for which
        // all child nodes resolutions have either completed or been cancelled. The cancelled descendant nodes are
        // tracked by the conflict resolution structures, so they won't be forgotten anyway, but they don't require a
        // new resolution of their ancestors. This is why it's ok to mark a parent as resolved in that case.
        val resolvedNodes = ConcurrentHashMap.newKeySet<DependencyNode>()

        var nodesToResolve = listOf(root)
        while(nodesToResolve.isNotEmpty()) {
            coroutineScope {
                nodesToResolve.forEach { node ->
                    node.launchRecursiveResolution(level, conflictResolver, resolvedNodes)
                }
            }

            nodesToResolve = conflictResolver.resolveConflicts()

            // Some candidates may have been resolved entirely before the conflict was detected and the resolution
            // cancelled, so we need to "unmark" them as resolved because their dependency may have changed after
            // conflict resolution (requiring a new resolution).
            resolvedNodes.removeAll(nodesToResolve)
        }
    }

    /**
     * Launches a new coroutine to resolve this [DependencyNode], keeping track of the resolution job in the node cache.
     */
    context(CoroutineScope)
    private fun DependencyNode.launchRecursiveResolution(
        level: ResolutionLevel,
        conflictResolver: ConflictResolver,
        resolvedNodes: MutableSet<DependencyNode>,
    ) {
        // If a conflict causes the cancellation of all jobs, and this cancellation happens between the moment when the new
        // job is launched and the moment it is registered for tracking, we could miss the cancellation.
        // This is ok because the launched job will then check for conflicts and end almost immediately.
        val job = launch {
            resolveRecursively(level, conflictResolver, resolvedNodes)
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
        conflictResolver: ConflictResolver,
        resolvedNodes: MutableSet<DependencyNode>,
    ) {
        if (this in resolvedNodes) {
            return // skipping already resolved node
        }
        resolutionMutex.withLock {
            // maybe a concurrent job has resolved this node already
            if (this in resolvedNodes) {
                return
            }

            val conflictDetected = conflictResolver.registerAndDetectConflicts(this)
            if (conflictDetected) {
                return // we don't want to resolve conflicted candidates in this wave
            }

            resolveChildren(level)
            coroutineScope {
                children.forEach { node ->
                    node.launchRecursiveResolution(level, conflictResolver, resolvedNodes)
                }
            }
            // We track that we finished resolving this node, because some resolutions can be cancelled half-way through
            // in case of conflicts
            resolvedNodes.add(this)
        }
    }

    /**
     * Downloads dependencies of all nodes by traversing a dependency graph.
     */
    suspend fun downloadDependencies() = root.distinctBfsSequence().distinctBy { it.key }.forEach { it.downloadDependencies() }
}

private class ConflictResolver(val conflictResolutionStrategies: List<ConflictResolutionStrategy>) {
    /**
     * Maps each key (group:artifact) to the list of "similar" nodes that have that same key, and thus are potential
     * sources of dependency conflicts.
     * The map needs to be thread-safe because we only protect with a mutex per dependency key (2 dependencies with
     * different keys can access the map at the same time).
     * The list values, however, aren't thread-safe, but they are key-specific.
     */
    private val similarNodesByKey = ConcurrentHashMap<Key<*>, MutableList<DependencyNode>>()
    private val conflictedKeys = ConcurrentHashMap.newKeySet<Key<*>>()
    private val conflictDetectionMutexByKey = StripedMutex(64)

    /**
     * Registers this node for potential conflict resolution, and returns whether it already conflicts with a previously
     * seen node. Can be called concurrently with any node, including those sharing the same key.
     */
    suspend fun registerAndDetectConflicts(node: DependencyNode): Boolean =
        conflictDetectionMutexByKey.getLock(node.key.hashCode()).withLock {
            val similarNodes = similarNodesByKey.computeIfAbsent(node.key) { mutableListOf() }
            similarNodes.add(node) // register the node for potential future conflict resolution
            if (node.key in conflictedKeys) {
                return true
            }
            if (similarNodes.size > 1 && similarNodes.containsConflicts()) {
                conflictedKeys += node.key
                // We don't want to keep resolving conflicting nodes, because it's potentially pointless.
                // They will be resolved in the next wave.
                similarNodes.forEach { it.resolutionJobs.forEach(Job::cancel) }
                return true
            }
            return false
        }

    private fun List<DependencyNode>.containsConflicts() = conflictResolutionStrategies.any {
        it.isApplicableFor(this) && it.seesConflictsIn(this)
    }

    /**
     * Resolves conflicts and returns the nodes that must be resolved as a result.
     * Must not be called concurrently with [registerAndDetectConflicts].
     */
    suspend fun resolveConflicts(): List<DependencyNode> = coroutineScope {
        conflictingNodes()
            .map { candidates ->
                async {
                    val resolved = candidates.resolveConflict()
                    if (resolved) {
                        candidates
                    } else {
                        emptyList()
                    }
                }
            }
            .awaitAll()
            .flatten()
            .also { conflictedKeys.clear() }
    }

    private fun conflictingNodes(): List<List<DependencyNode>> = conflictedKeys.map { key ->
        similarNodesByKey[key] ?: throw AmperDependencyResolutionException("Nodes are missing for ${key.name}")
    }

    private fun List<DependencyNode>.resolveConflict(): Boolean {
        val strategy = conflictResolutionStrategies.find { it.isApplicableFor(this) && it.seesConflictsIn(this) }
            ?: return true // if no strategy sees the conflict, there is no conflict so it is considered resolved
        return strategy.resolveConflictsIn(this)
    }
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
     * @return true if the new child nodes need to be resolved themselves recursively, or false if the node was already
     * resolved and the children should be skipped.
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
 * A mutex to protect the resolution of a node. This prevents 2 jobs from resolving the same node (and children), while
 * still allowing to spawn multiple jobs for the same node (which happens in case of diamonds).
 *
 * Why multiple jobs per node? Why not reuse a single job? The nodes are a graph, while structured concurrency is a tree
 * of jobs. We want to be able to cancel a subgraph of jobs without caring about whether another non-cancelled parent
 * requires one of the child dependencies: this parent just launches its own job for the node, and that one is not cancelled.
 */
// TODO this should probably be an internal property of the dependency node instead of being stored in the nodeCache
private val DependencyNode.resolutionMutex: Mutex
    get() = context.nodeCache.computeIfAbsent(Key<Mutex>("resolutionMutex")) { Mutex() }

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
