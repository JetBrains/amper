/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.amper.concurrency.withLock
import org.jetbrains.amper.telemetry.use
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.collections.get
import kotlin.io.path.exists

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
 * @see DependencyNodeHolder
 * @see MavenDependencyNode
 * @see Context
 */
class Resolver {

    /**
     * Builds a dependency graph starting from [root].
     * Can be called multiple times on the same instance to perform resolution with or without network access.
     * One can get a faster result with [ResolutionLevel.LOCAL],
     * but it might be incomplete if not all previous dependencies were downloaded.
     *
     * @param level whether resolution should be performed with (default) or without network access
     * @return current instance
     */
    suspend fun buildGraph(
        root: DependencyNode,
        level: ResolutionLevel = ResolutionLevel.NETWORK,
        transitive: Boolean = true,
        unspecifiedVersionResolver: UnspecifiedVersionResolver<MavenDependencyNode>? = null
    ) {
        root.context.spanBuilder("Resolver.buildGraph").use {
            val conflictResolver =
                ConflictResolver(root.context.settings.conflictResolutionStrategies, unspecifiedVersionResolver)

            // Contains all nodes that we resolved (we populated the children of their internal dependency), and for which
            // all child nodes resolutions have either completed or been canceled.
            // The canceled descendant nodes are
            // tracked by the conflict resolution structures, so they won't be forgotten anyway, but they don't require a
            // new resolution of their ancestors. This is why it's ok to mark a parent as resolved in that case.
            val resolvedNodes = ConcurrentHashMap.newKeySet<DependencyNode>()

            var nodesToResolve = setOf(root)
            while (nodesToResolve.isNotEmpty()) {
                coroutineScope {
                    nodesToResolve.forEach { node ->
                        node.launchRecursiveResolution(level, conflictResolver, resolvedNodes, transitive)
                    }
                }

                nodesToResolve = conflictResolver.resolveConflicts()

                // Some candidates may have been resolved entirely before the conflict was detected
                // and the resolution canceled.
                // So we need to "unmark" them as resolved because their dependency may have changed after
                // conflict resolution (requiring a new resolution).
                resolvedNodes.removeAll(nodesToResolve.toSet())
            }
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
        transitive: Boolean
    ) {
        // If a conflict causes the cancellation of all jobs, and this cancellation happens between the moment when the new
        // job is launched and the moment it is registered for tracking, we could miss the cancellation.
        // This is ok because the launched job will then check for conflicts and end almost immediately.
        val job = launch {
            resolveRecursively(level, conflictResolver, resolvedNodes, transitive)
        }
        // This ensures the job is removed from the list upon completion even in cases where it is canceled
        // before it even starts. We can't achieve this with a try-finally inside the coroutine itself.
        job.invokeOnCompletion {
            resolutionJobs.remove(job)
        }
        resolutionJobs.add(job)
    }

    context(CoroutineScope)
    private suspend fun DependencyNode.resolveRecursively(
        level: ResolutionLevel,
        conflictResolver: ConflictResolver,
        resolvedNodes: MutableSet<DependencyNode>,
        transitive: Boolean
    ) {
        if (this in resolvedNodes) {
            // Register this node and its children to the conflictResolver,
            // since it might be a resolved node from the canceled resolution
            // All nodes resolved during the canceled resolution were unregistered from conflict resolver.
            // So, they should be re-registered there as soon as those nodes are started being used in a graph again.
            conflictResolver.registerAndDetectConflictsWithChildren(this)
            return // skipping already resolved node
        }
        resolutionMutex.withLock {
            // maybe a concurrent job has resolved this node already
            if (this in resolvedNodes)   {
                conflictResolver.registerAndDetectConflictsWithChildren(this)
                return
            }

            val conflictDetected = conflictResolver.registerAndDetectConflicts(this)
            if (conflictDetected) {
                return // we don't want to resolve conflicted candidates in this wave
            }

            resolveChildren(level, transitive)

            coroutineScope {
                children.forEach { node ->
                    node.launchRecursiveResolution(level, conflictResolver, resolvedNodes, transitive)
                }
            }
            // We track that we finished resolving this node because some resolutions can be canceled half-way through
            // in case of conflicts
            resolvedNodes.add(this)
        }
    }

    /**
     * Downloads dependencies of all nodes by traversing a dependency graph.
     */
    suspend fun downloadDependencies(node: DependencyNode, downloadSources: Boolean = false) {
        node.context.spanBuilder("Resolver.downloadDependencies").use {
            coroutineScope {
                node
                    .distinctBfsSequence { child, _ -> child !is MavenDependencyConstraintNode }
                    .distinctBy { (it as? MavenDependencyNode)?.dependency ?: it }
                    .forEach {
                        launch {
                            it.downloadDependencies(downloadSources)
                        }
                    }
            }

            node.closeResolutionContexts()
        }
    }

    private suspend fun DependencyNode.closeResolutionContexts() {
        coroutineScope {
            distinctBfsSequence { child, _ -> child !is MavenDependencyConstraintNode }
                .distinctBy { (it as? MavenDependencyNode)?.dependency ?: it }
                .map { it.context }.distinct()
                .forEach {
                    launch {
                        it.close()
                    }
                }
        }
    }
}

private class ConflictResolver(
    val conflictResolutionStrategies: List<ConflictResolutionStrategy>,
    unspecifiedVersionResolver: UnspecifiedVersionResolver<MavenDependencyNode>? = null
) {
    /**
     * Maps each key (group:artifact) to the list of "similar" nodes that have that same key, and thus are potential
     * sources of dependency conflicts.
     * The map needs to be thread-safe because we only protect it with a mutex per-dependency key (two dependencies with
     * different keys can access the map at the same time).
     * The list values, however, aren't thread-safe, but they are key-specific.
     */
    private val similarNodesByKey = ConcurrentHashMap<Key<*>, MutableSet<DependencyNode>>()
    private val conflictedKeys = ConcurrentHashMap.newKeySet<Key<*>>()
    private val conflictDetectionMutexByKey = StripedMutex(64)

    private val unspecifiedVersionHelper = unspecifiedVersionResolver?.let { UnspecifiedMavenDependencyVersionHelper(it) }

    /**
     * Registers this node and all its children transitively for potential conflict resolution
     * if the node has not been seen yet.
     * Can be called concurrently with any node, including those sharing the same key.
     */
    suspend fun registerAndDetectConflictsWithChildren(node: DependencyNode) {
        conflictDetectionMutexByKey.withLock(node.key.hashCode()) {
            val similarNodes = similarNodesByKey.computeIfAbsent(node.key) { mutableSetOf() }

            if (similarNodes.contains(node)) {
                return
            }
        }

        // Node is not known to conflict resolver, register it together with all children transitively
        node.distinctBfsSequence().forEach {
            registerAndDetectConflicts(it)
        }
    }

    /**
     * Unregister all nodes that are no longer reachable from the root of the graph after conflicts were resolved.
     * It is called non-concurrently at the end of resolution iteration after resolution finished and before conflicting nodes
     * are started resolving on the next iteration.
     *
     * As a result of conflict resolution, nodes may start referencing another version of maven dependency with a different list of children.
     * Old children could be no longer referenced by any node in a graph,
     * such nodes (both dependencies and dependencyConstraints) should no longer influence conflict resolution logic.
     *
     * // todo (AB) : Subgraphs of conflicting nodes could have included nodes referenced from another node in graph outside of this subgraphs
     * // todo (AB) : Such nodes are still reachable in graph, but the list of their parents contains nodes from
     * // todo (AB) : conflicting subgraphs that are no longer a part of the graph.
     * // todo (AB) : It is important to note that some of those nodes might be re-added to the graph
     * // todo (AB) : as a part of conflict resolution winning subgraph, but some will be eliminated (not reachable from the root).
     * // todo (AB) : The question is: how to detect such "stale" parents? how to get rid of them on conflict resolution-loosing subgraphs?
     */
    private suspend fun unregisterOrphanNodes(nodes: Collection<DependencyNode>) {
        nodes.flatMap {
            it.distinctBfsSequence { child, _ ->
            // the only parent leads to the unregistered top node
                // => the node could be unregistered as well with all children
                child.parents.size == 1
                        // all parents lead to one of the unregistered top nodes
                        // => the node could be unregistered as well with all children
                        || !child.isThereAPathToTopBypassing(nodes)
                // otherwise, the node is referenced from some resolved non-conflicted node
                // => it should be kept with all children (avoid unregistering)
            }
        }.forEach {
            conflictDetectionMutexByKey.withLock(it.key.hashCode()) {
                val similarNodes = similarNodesByKey.computeIfAbsent(it.key) { mutableSetOf() }
                similarNodes.remove(it)
                unspecifiedVersionHelper?.unregisterNode(it)
            }
        }
    }

    private fun DependencyNode.isThereAPathToTopBypassing(nodes: Collection<DependencyNode>): Boolean {
        if (parents.isEmpty()) return true // we reach the root

        val filteredParents = parents - nodes
        if (filteredParents.isEmpty()) return false // node has parents, but all of them are among those we should bypass along the way to root

        return filteredParents.any { it.isThereAPathToTopBypassing(nodes) }
    }

    /**
     * Registers this node for potential conflict resolution and returns whether it already conflicts with a previously
     * seen node. Can be called concurrently with any node, including those sharing the same key.
     */
    suspend fun registerAndDetectConflicts(node: DependencyNode): Boolean =
        conflictDetectionMutexByKey.withLock(node.key.hashCode()) {
            val similarNodes = similarNodesByKey.computeIfAbsent(node.key) { mutableSetOf() }
            similarNodes.add(node) // register the node for potential future conflict resolution

            unspecifiedVersionHelper?.registerNode(node) // register the node for potential future version resolution

            if (node.key in conflictedKeys) {
                return true
            }
            if (similarNodes.size > 1 && similarNodes.containsConflicts()) {
                conflictedKeys += node.key
                // We don't want to keep resolving conflicting nodes because it's potentially pointless.
                // They will be resolved in the next wave.
                similarNodes.forEach { it.resolutionJobs.forEach(Job::cancel) }
                return true
            }
            return false
        }

    private fun Collection<DependencyNode>.containsConflicts() = conflictResolutionStrategies.any {
        it.isApplicableFor(this) && it.seesConflictsIn(this)
    }

    /**
     * Resolves conflicts and returns the nodes that must be resolved as a result.
     * Must not be called concurrently with [registerAndDetectConflicts].
     */
    suspend fun resolveConflicts(): Set<DependencyNode> = coroutineScope {
        conflictingNodes()
            .map { candidates ->
                async {
                    val resolved = candidates.resolveConflict()
                    if (resolved) {
                        candidates
                    } else {
                        emptySet()
                    }
                }
            }
            .awaitAll()
            .flatten<DependencyNode>()
            .also {
                unregisterOrphanNodes(it)
                conflictedKeys.clear()
            }
            .toSet()
            .let {
                it + (unspecifiedVersionHelper?.resolveVersions() ?: emptyList())
            }
    }

    private fun conflictingNodes(): List<Set<DependencyNode>> = conflictedKeys.map { key ->
        similarNodesByKey[key] ?: throw AmperDependencyResolutionException("Nodes are missing for ${key.name}")
    }

    private fun Collection<DependencyNode>.resolveConflict(): Boolean {
        val strategy = conflictResolutionStrategies.find { it.isApplicableFor(this) && it.seesConflictsIn(this) }
            ?: return true // if no strategy sees the conflict, there is no conflict, so it is considered resolved
        return strategy.resolveConflictsIn(this)
    }
}

private class UnspecifiedMavenDependencyVersionHelper(val unspecifiedVersionProvider: UnspecifiedVersionResolver<MavenDependencyNode>) {
    private val unversionedNodesByKey = ConcurrentHashMap<Key<*>, MutableSet<MavenDependencyNode>>()

    fun registerNode(node: DependencyNode) = doIfApplicable(node) {
        val unversionedNodes = unversionedNodesByKey.computeIfAbsent(node.key) { mutableSetOf() }
        unversionedNodes.add(it)
    }

    fun unregisterNode(node: DependencyNode) = doIfApplicable(node) {
        val unversionedNodes = unversionedNodesByKey.computeIfAbsent(node.key) { mutableSetOf() }
        unversionedNodes.remove(it)
    }

    private fun doIfApplicable(node: DependencyNode, block: (MavenDependencyNode) -> Unit) {
        if (node is MavenDependencyNode && node.resolvedVersion() == null && unspecifiedVersionProvider.isApplicable(node)) {
            block(node)
        }
    }

    /**
     * Try to resolve unspecified dependency versions.
     *
     * @return successfully resolved unspecified versions
     */
    fun resolveVersions(): List<MavenDependencyNode> {
        return unversionedNodesByKey.values.flatMap { nodes ->
            val resolvedNodes = unspecifiedVersionProvider.resolveVersions(nodes)

            resolvedNodes.forEach { (node, resolvedVersion) ->
                node.dependency = node.context.createOrReuseDependency(node.group, node.module, resolvedVersion)
                nodes.remove(node)
            }

            resolvedNodes.keys
        }
    }
}

/**
 * A dependency node is a graph element.
 *
 * It has the following properties.
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
    suspend fun resolveChildren(level: ResolutionLevel, transitive: Boolean)

    /**
     * Ensures that the dependency-relevant files are on disk, according to settings.
     */
    suspend fun downloadDependencies(downloadSources: Boolean = false)

    /**
     * Returns a sequence of distinct nodes using BFS starting at (and including) this node.
     *
     * The given [childrenPredicate] can be used to skip parts of the graph.
     * If [childrenPredicate] is false for a node, the node is skipped and will not appear in the sequence.
     * The subgraph of the skipped node is also not traversed, so these descendant nodes won't be in the sequence unless
     * they are reached via some other node.
     * Using [childrenPredicate] is, therefore, different from filtering the resulting sequence after the fact.
     *
     * The nodes are distinct in terms of referential identity, which is enough to eliminate duplicate "requested"
     * dependency triplets. This does NOT eliminate nodes that requested the same dependency in different versions,
     * even though conflict resolution should make them point to the same dependency version internally eventually.
     *
     * The returned sequence is guaranteed to be finite, as it prunes the graph when encountering duplicates (and thus cycles).
     */
    fun distinctBfsSequence(childrenPredicate: (DependencyNode, DependencyNode) -> Boolean = { _,_ -> true }): Sequence<DependencyNode> = sequence {
        val queue = LinkedList(listOf(this@DependencyNode))
        val visited = mutableSetOf<DependencyNode>()
        while (queue.isNotEmpty()) {
            val node = queue.remove()
            if (visited.add(node)) {
                yield(node)
                queue.addAll(node.children.filter { it !in visited && childrenPredicate(it, node) })
            }
        }
    }

    /**
     * Prints the graph below the node using a Gradle-like output style.
     */
    fun prettyPrint(forMavenNode: MavenCoordinates? = null): String = buildString {
        val allMavenDepsKeys = distinctBfsSequence()
            .map { it.unwrap() }
            .filterIsInstance<MavenDependencyNode>()
            .groupBy { it.key }
        prettyPrint(this, allMavenDepsKeys,forMavenNode = forMavenNode)
    }

    private fun DependencyNode.unwrap(): DependencyNode =
        when (this) {
            is DependencyNodeWithChildren -> node.unwrap()
            else -> this
        }

    private fun prettyPrint(
        builder: StringBuilder,
        allMavenDepsKeys: Map<Key<MavenDependency>, List<MavenDependencyNode>>,
        indent: StringBuilder = StringBuilder(),
        visited: MutableSet<Pair<Key<*>, Any?>> = mutableSetOf(),
        addLevel: Boolean = false,
        forMavenNode: MavenCoordinates? = null
    ) {
        val thisUnwrapped = unwrap()
        builder.append(indent).append(toString())

        // key doesn't include a version on purpose,
        // but different nodes referencing the same MavenDependency result in the same dependencies
        // => add no need to distinguish those while pretty printing
        val seen = !visited.add(key to ((thisUnwrapped as? MavenDependencyNode)?.dependency ?: (thisUnwrapped as? MavenDependencyConstraintNode)?.dependencyConstraint))
        if (seen && children.any { it.shouldBePrinted(allMavenDepsKeys, forMavenNode) }) {
            builder.append(" (*)")
        } else if (thisUnwrapped is MavenDependencyConstraintNode) {
            builder.append(" (c)")
        }
        builder.append('\n')
        if (seen || children.isEmpty()) {
            return
        }

        if (indent.isNotEmpty()) {
            indent.setLength(indent.length - 5)
            if (addLevel) {
                indent.append("│    ")
            } else {
                indent.append("     ")
            }
        }

        children
            .filter { it.shouldBePrinted(allMavenDepsKeys, forMavenNode) }
            .let { filteredNodes ->
                filteredNodes.forEachIndexed { i, it ->
                    val addAnotherLevel = i < filteredNodes.size - 1
                    if (addAnotherLevel) {
                        indent.append("├─── ")
                    } else {
                        indent.append("╰─── ")
                    }
                    it.prettyPrint(builder, allMavenDepsKeys, indent, visited, addAnotherLevel, forMavenNode)
                    indent.setLength(indent.length - 5)
                }
            }
    }

    fun DependencyNode.shouldBePrinted(
        allMavenDepsKeys: Map<Key<MavenDependency>, List<MavenDependencyNode>>,
        forMavenNode: MavenCoordinates? = null
    ): Boolean = unwrap().let {
        it !is MavenDependencyConstraintNode || it.isConstraintAffectingTheGraph(
            allMavenDepsKeys,
            forMavenNode
        )
    }

    fun MavenDependencyConstraintNode.isConstraintAffectingTheGraph(
        allMavenDepsKeys: Map<Key<MavenDependency>, List<MavenDependencyNode>>,
        forMavenNode: MavenCoordinates?
    ): Boolean =
        allMavenDepsKeys[this.key]?.let { affectedNode ->
            // If there is a dependency with the original version equal to the constraint version, then constraint is noop.
            affectedNode.none { dep ->
                dep.version == dep.dependency.version && dep.version == this.version.resolve()
            }
                    &&
                    // The constraint version is the same as the resulted dependency version,
                    // and the original dependency version is different (⇒ constraint might have affected resolution)
                    affectedNode.any { dep ->
                        this.version.resolve() == dep.dependency.version && dep.version != dep.dependency.version
                    }
        } ?: forMavenNode?.let { group == it.groupId && module == it.artifactId }
        ?: false

    suspend fun dependencyPaths(nodeBlock: (DependencyNode) -> Unit = {}): List<Path> {
        val files = mutableSetOf<Path>()
        for (node in distinctBfsSequence()) {
            if (node is MavenDependencyNode) {
                node.dependency
                    .files()
                    .mapNotNull { it.getPath() }
                    .forEach { file ->
                        check(file.exists()) {
                            "File '$file' was returned from dependency resolution, but is missing on disk"
                        }
                        files.add(file)
                    }
            }
            nodeBlock(node)
        }
        return files.toList()
    }
}

/**
 * A mutex to protect the resolution of a node.
 * This prevents two jobs from resolving the same node (and children), while
 * still allowing to spawn multiple jobs for the same node (which happens in case of diamonds).
 *
 * Why multiple jobs per node? Why not reuse a single job? The nodes are a graph, while structured concurrency is a tree
 * of jobs. We want to be able to cancel a subgraph of jobs without caring about whether another non-canceled parent
 * requires one of the child dependencies: this parent just launches its own job for the node, and that one is not canceled.
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
 * Describes the state of the dependency resolution process.
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

/**
 * Provide versions for nodes that were initially added to the resolution graph without a version.
 * Such a version could have been known after some subgraph resolution has finished.
 *
 * For instance, maven dependencies specified without versions could be supplied with versions as soon as imported BOM
 * is resolved.
 */
interface UnspecifiedVersionResolver<T> {
    fun isApplicable(node: T): Boolean
    fun resolveVersions(nodes: Set<T>): Map<T, String>
}

class Progress

class AmperDependencyResolutionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
