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
import org.jetbrains.amper.dependency.resolution.DependencyGraph.Companion.toGraph
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.pathString
import kotlin.time.Clock
import kotlin.time.Instant

private val logger = LoggerFactory.getLogger("dr/resolver.kt")

/**
 * This is the entry point to the library.
 *
 * The suggested usage is as follows.
 *
 * 1. Create a resolver instance providing an initial graph of nodes.
 * 2. Build a dependency graph, optionally providing a desired [ResolutionLevel].
 * 3. Optionally, rebuild the graph by providing a higher [ResolutionLevel].
 * 4. Traverse the graph manually or [downloadDependencies] at once.
 *
 * This is how one can download 'kotlin-test' and all its JVM dependencies from Maven Central.
 *
 * ```Kotlin
 * val node = MavenDependencyNodeWithContext(
 *   Context {
 *     scope = ResolutionScope.COMPILE
 *     platforms = setOf(ResolutionPlatform.JVM)
 *   },
 *   "org.jetbrains.kotlin", "kotlin-test", "1.9.20",
 *   isBom = false
 * )
 * val resolvedGraph = Resolver().resolveDependencies(node)
 * val paths = resolvedGraph.dependencyPaths()
 * ```
 *
 * Sample below allows resolving several maven dependencies in the same resolution context
 *
 * ```
 * suspend fun List<MavenCoordinates>.foo(repositories: List<Repository>): List<Path> {
 *   val resolutionContext = Context {
 *     this.scope = ResolutionScope.COMPILE
 *     this.platforms = setOf(ResolutionPlatform.JVM)
 *     this.repositories = repositories
 *   }
 *
 *   val root = RootDependencyNodeWithContext(
 *     templateContext = resolutionContext,
 *     children = this.map {
 *       MavenDependencyNodeWithContext(
 *         resolutionContext,
 *         coordinates = it,
 *         isBom = false
 *       )
 *     },
 *   )
 *
 *   val resolvedGraph = Resolver().resolveDependencies(root)
 *   return resolvedGraph.dependencyPaths()
 * }
 *```
 *
 * @see DependencyNodeHolderWithContext
 * @see MavenDependencyNode
 * @see Context
 */
class Resolver {

    /**
     * Main entry point into the dependency resolution.
     * It receives an unresolved input graph as the input,
     * resolves transitive dependencies if requested (default behavior) and downloads artifacts corresponding
     * to the nodes of the resolved graph.
     *
     * @param root
     * represents the root node of the input dependencies graph to be resolved.
     * To resolve single maven dependency [MavenDependencyNodeWithContext] might be used,
     * to resolve a list of dependencies [RootDependencyNodeWithContext] might be used,
     * specifying an input dependencies list as children of the root node.
     * Consumers might use other nodes for grouping dependencies, the only requirements to such nodes is that those
     * should be serializable if a consumer wants to benefit from graph resolution cache and specifies non-null [incrementalCacheUsage].
     * See more details on serialization in [org.jetbrains.amper.dependency.resolution.GraphSerializableTypesProvider]
     *
     * @param resolutionLevel
     * If [ResolutionLevel.LOCAL] is passed, dependencies and their artifacts are resolved from
     * local file storage only, if artifact is missing there it is left unresolved. With this resolution level, DR doesn't
     * use network at all.
     * [ResolutionLevel.NETWORK] implies full resolution. If node metadata or artifact is messing locally, it is resolved
     * from the repositories (defined in the resolution context of the node).
     *
     * @param downloadSources Artifacts containing source and documentation (sources, Javadocs) are downloaded from the repositories together
     * with the main artifacts if this flag is set to true.
     *
     * @param transitive Resolve the direct dependencies from the input graph only without transitive ones.
     *
     * @param incrementalCacheUsage instance of the incremental cache to get cached resolution graph from (and store to).
     *
     * @param unspecifiedVersionResolver instance of the [UnspecifiedVersionResolver] to resolve version of dependencies
     * that don't have a version specified in the input graph (or transitive dependencies with an unspecified version as well)
     *
     * @param postProcessDeserializedGraph callback to be called after the graph is resolved and deserialized from the cache.
     * It is useful if some data in the nput graph can't be serialized and thus is marked as [kotlinx.serialization.Transient],
     * but still has to be populated from the input graph to the restored one before it is started using.
     *
     * @return resolved dependencies graph
     */
    suspend fun resolveDependencies(
        root: DependencyNodeWithContext,
        resolutionLevel: ResolutionLevel = ResolutionLevel.NETWORK,
        downloadSources: Boolean = false,
        transitive: Boolean = true,
        incrementalCacheUsage: IncrementalCacheUsage = IncrementalCacheUsage.USE,
        unspecifiedVersionResolver: UnspecifiedVersionResolver<MavenDependencyNodeWithContext>? = null,
        postProcessDeserializedGraph: (SerializableDependencyNode) -> Unit = {},
    ): ResolvedGraph {
        return root.context.spanBuilder("DR.graph:resolveDependencies").use {
            val resolutionId = getContextAwareRootCacheEntryKey(root, transitive, downloadSources, resolutionLevel)

            val incrementalCache = root.context.settings.incrementalCache

            if (resolutionId == null
                || incrementalCacheUsage == IncrementalCacheUsage.SKIP
                || incrementalCache == null
            ) {
                root.resolveDependencies(resolutionLevel, transitive, downloadSources, unspecifiedVersionResolver)
                root.toResolvedGraph()
            } else {
                val graphEntryKeys = getDependenciesGraphInput(root)
                if (graphEntryKeys.contains(CacheEntryKey.NotCached)) {
                    root.resolveDependencies(resolutionLevel, transitive, downloadSources, unspecifiedVersionResolver)
                    root.toResolvedGraph()
                } else {
                    var graphResolvedInsideCache: ResolvedGraph? = null

                    val cacheInputValues = mapOf(
                        "userCacheRoot" to root.context.settings.fileCache.amperCache.pathString,
                        "dependencies" to graphEntryKeys.joinToString("|") { "${ it.computeKey() }" },
                    )

                    val resolvedGraph = try {
                        incrementalCache.execute(
                            key = resolutionId,
                            cacheInputValues,
                            forceRecalculation = (incrementalCacheUsage == IncrementalCacheUsage.REFRESH_AND_USE),
                            inputFiles = listOf(),
                        ) {
                            root.context.spanBuilder("DR.graph:resolution")
                                .setAttribute(
                                    "configuration",
                                    cacheInputValues["dependencies"]
                                ) // todo (AB) : Remove it (was added for debugging purposes))
                                .setAttribute(
                                    "userCacheRoot",
                                    cacheInputValues["userCacheRoot"]
                                ) // todo (AB) : Remove it (was added for debugging purposes))
                                .setAttribute(
                                    "resolutionId",
                                    resolutionId
                                ) // todo (AB) : Remove it (was added for debugging purposes))
                                .use {
                                    root.resolveDependencies(resolutionLevel, transitive, downloadSources, unspecifiedVersionResolver)
                                    graphResolvedInsideCache = root.toResolvedGraph()

                                    val serializableGraph = graphResolvedInsideCache.root.toGraph()
                                    val serialized = GraphJson.json.encodeToString(serializableGraph)
                                    IncrementalCache.ExecutionResult(
                                        graphResolvedInsideCache.root.dependencyPaths(),
                                        mapOf("graph" to serialized),
                                        expirationTime = graphResolvedInsideCache.expirationTime
                                    )
                                }
                        }.let {
                            if (graphResolvedInsideCache != null) {
                                graphResolvedInsideCache
                            } else {
                                val serialized = it.outputValues["graph"]!!
                                val deserializedGraph: DependencyGraph = root.context.spanBuilder("DR.graph:deserialization").use {
                                    GraphJson.json.decodeFromString<DependencyGraph>(serialized)
                                }
                                val resolvedGraph = deserializedGraph.root

                                // Post-process deserialized graph enriching it with the state from the input graph.
                                // Any exception thrown during this phase results in fallback to non-cached resolution of the input graph
                                postProcessDeserializedGraph(resolvedGraph)

                                ResolvedGraph(resolvedGraph, it.expirationTime)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error(
                            "Unable to calculate dependency graph based on incremental cache, " +
                                    "falling back to non-cached resolution: ${e.toString()}",
                            e
                        )
                        if (graphResolvedInsideCache != null) {
                            graphResolvedInsideCache
                        } else {
                            // todo (AB) : Invalidate cache entry manually on any failure
                            // todo (AB) : (might be not needed though if cache.codeVersion is properly specified)

                            // Fallback to non-cached resolution
                            root.resolveDependencies(resolutionLevel, transitive, downloadSources, unspecifiedVersionResolver)
                            root.toResolvedGraph()
                        }
                    }

                    resolvedGraph
                }
            }
        }
    }

    /**
     * Calculate the expiration time of the graph represented with this node.
     * Collect environment context used for resolution of the graph.
     * Wrap it all into resulting [ResolvedGraph]
     *
     * Note:
     * Among others, a graph expiration is calculated based on the expiration time of the snapshot files,
     * those times are absent in the serialized graph,
     * therefore, calculation of the expiration time of arbitrary DependencyNode is not possible
     * (nodes restored from cache don't know about expiration time).
     * Having per-node expiration time might be needed for the mixed graph resolution
     * (where some nodes were already resolved and might have been taken from cache),
     * in that case dependency node expiration time should be started serializing and taken into account when
     * deciding whether to use the nodes restored from cache or not.
     */
    private suspend fun DependencyNodeWithContext.toResolvedGraph(): ResolvedGraph {
        val expirationTime = try {
            distinctBfsSequence()
                .toSet()
                .mapNotNull { it.calculateNodeExpirationTime() }
                .minByOrNull { it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Unable to calculate expiration time of the dependency graph", e)
            null
        }

        return ResolvedGraph(this, expirationTime)
    }

    private suspend fun DependencyNode.calculateNodeExpirationTime(): Instant? {
        val snapshotExpirationTime: Instant? =
            (this as? MavenDependencyNodeWithContext)?.let { node ->
                node.dependency.files(false)
                    .filterIsInstance<SnapshotDependencyFileImpl>()
                    .mapNotNull { it.getExpirationTime() }
                    .minByOrNull { it }
            }

        val isNotCacheable = this.messages.any { it.severity >= Severity.ERROR && !it.cacheable }
        val expirationDueToRecoverableErrorTime = if (isNotCacheable) Clock.System.now() else null

        return if (snapshotExpirationTime != null && expirationDueToRecoverableErrorTime != null) {
            listOf(snapshotExpirationTime, expirationDueToRecoverableErrorTime)
                .minByOrNull { it }
        } else {
            snapshotExpirationTime ?: expirationDueToRecoverableErrorTime
        }
    }

    // todo (AB) : Add test (dependencies order matters, moving dependency from one module to another matters as well)
    internal fun getDependenciesGraphInput(node: DependencyNodeWithContext): List<CacheEntryKey> {
        val cacheEntryKeys: MutableList<CacheEntryKey> = mutableListOf()
        node.bfsSequence(includeDuplicates = true).forEach {
            // skip the parent node, its cacheEntryKey is used as a cache entry identifier
            // and is not a part of cache configuration that affects entry invalidation
            if (it == node) return@forEach

            if (it !is DependencyNodeWithContext) {
                cacheEntryKeys.add(CacheEntryKey.NotCached)
                return cacheEntryKeys
            }

            val cacheEntryKey = it.getParentAwareCacheEntryKey()
            cacheEntryKeys.add(cacheEntryKey)

            if (cacheEntryKey == CacheEntryKey.NotCached) {
                return cacheEntryKeys
            }
        }
        return cacheEntryKeys
    }

    private fun getContextAwareRootCacheEntryKey(
        root: DependencyNodeWithContext,
        transitive: Boolean,
        downloadSources: Boolean,
        resolutionLevel: ResolutionLevel
    ): String? {
        val cacheEntryKey = when (val rootCacheEntryKey = root.cacheEntryKey) {
            is CacheEntryKey.NotCached -> CacheEntryKey.NotCached
            is CacheEntryKey.CompositeCacheEntryKey -> {
                rootCacheEntryKey.copy(
                    components = rootCacheEntryKey.components + listOfNotNull(
                        ResolutionConfigPlain(root.context.settings),
                        downloadSources,
                        transitive,
                        resolutionLevel
                    )
                )
            }
        }.computeKey()

        return cacheEntryKey
    }

    private suspend fun DependencyNodeWithContext.resolveDependencies(
        resolutionLevel: ResolutionLevel,
        transitive: Boolean,
        downloadSources: Boolean,
        unspecifiedVersionResolver: UnspecifiedVersionResolver<MavenDependencyNodeWithContext>?
    ) {
        val resolver = Resolver()
        resolver.buildGraph(
            this,
            level = resolutionLevel,
            transitive = transitive,
            unspecifiedVersionResolver = unspecifiedVersionResolver,
        )
        resolver.downloadDependencies(this, downloadSources)
    }

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
        root: DependencyNodeWithContext,
        level: ResolutionLevel = ResolutionLevel.NETWORK,
        transitive: Boolean = true,
        unspecifiedVersionResolver: UnspecifiedVersionResolver<MavenDependencyNodeWithContext>? = null,
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
    context(coroutineScope: CoroutineScope)
    private fun DependencyNodeWithContext.launchRecursiveResolution(
        level: ResolutionLevel,
        conflictResolver: ConflictResolver,
        resolvedNodes: MutableSet<DependencyNode>,
        transitive: Boolean
    ) {
        // If a conflict causes the cancellation of all jobs, and this cancellation happens between the moment when the new
        // job is launched and the moment it is registered for tracking, we could miss the cancellation.
        // This is ok because the launched job will then check for conflicts and end almost immediately.
        val job = coroutineScope.launch {
            resolveRecursively(level, conflictResolver, resolvedNodes, transitive)
        }
        // This ensures the job is removed from the list upon completion even in cases where it is canceled
        // before it even starts. We can't achieve this with a try-finally inside the coroutine itself.
        job.invokeOnCompletion {
            resolutionJobs.remove(job)
        }
        resolutionJobs.add(job)
    }

    private suspend fun DependencyNodeWithContext.resolveRecursively(
        level: ResolutionLevel,
        conflictResolver: ConflictResolver,
        resolvedNodes: MutableSet<DependencyNode>,
        transitive: Boolean,
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
            if (this in resolvedNodes) {
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
     * Downloads dependencies of all nodes by traversing a dependency graph built by the method [buildGraph].
     */
    suspend fun downloadDependencies(node: DependencyNodeWithContext, downloadSources: Boolean = false) {
        node.context.spanBuilder("Resolver.downloadDependencies").use {
            coroutineScope {
                node
                    .distinctBfsSequence { child, _ -> child !is MavenDependencyConstraintNode }
                    .distinctBy { (it as? MavenDependencyNode)?.dependency ?: it }
                    .filterIsInstance<DependencyNodeWithContext>()
                    .forEach {
                        launch {
                            it.downloadDependencies(downloadSources)
                        }
                    }
            }

            node.closeResolutionContexts()
        }
    }

    private suspend fun DependencyNodeWithContext.closeResolutionContexts() {
        coroutineScope {
            distinctBfsSequence { child, _ -> child !is MavenDependencyConstraintNode }
                .distinctBy { (it as? MavenDependencyNode)?.dependency ?: it }
                .mapNotNull { (it as? DependencyNodeWithContext)?.context }.distinct()
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
    unspecifiedVersionResolver: UnspecifiedVersionResolver<MavenDependencyNodeWithContext>? = null
) {
    /**
     * Maps each key (group:artifact) to the list of "similar" nodes that have that same key, and thus are potential
     * sources of dependency conflicts.
     * The map needs to be thread-safe because we only protect it with a mutex per-dependency key (two dependencies with
     * different keys can access the map at the same time).
     * The list values, however, aren't thread-safe, but they are key-specific.
     */
    private val similarNodesByKey = ConcurrentHashMap<Key<*>, MutableSet<DependencyNodeWithContext>>()
    private val conflictedKeys = ConcurrentHashMap.newKeySet<Key<*>>()
    private val conflictDetectionMutexByKey = StripedMutex(64)

    private val unspecifiedVersionHelper = unspecifiedVersionResolver?.let { UnspecifiedMavenDependencyVersionHelper(it) }

    /**
     * Registers this node and all its children transitively for potential conflict resolution
     * if the node has not been seen yet.
     * Can be called concurrently with any node, including those sharing the same key.
     */
    suspend fun registerAndDetectConflictsWithChildren(node: DependencyNodeWithContext) {
        conflictDetectionMutexByKey.withLock(node.key.hashCode()) {
            val similarNodes = similarNodesByKey.computeIfAbsent(node.key) { mutableSetOf() }

            if (similarNodes.contains(node)) {
                return
            }
        }

        // Node is not known to conflict resolver, register it together with all children transitively
        node.distinctBfsSequence()
            .filterIsInstance<DependencyNodeWithContext>()
            .forEach {
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
    private suspend fun unregisterOrphanNodes(nodes: Map<DependencyNodeWithContext, List<DependencyNodeWithContext>>) {
        val nodesToUnregister = nodes.keys +
                nodes.values.flatMap { oldChildren ->
                    // old children of the node before the conflict was resolved
                    oldChildren
                        .toSet()
                        .filter { it.isOrphanChildOfConflictingNodes(nodes) }
                        .flatMap {
                            it.distinctBfsSequence { child, _ ->
                                // only a single parent leads to the unregistered top node
                                // => the node can be unregistered as well with all children
                                child.isOrphanChildOfConflictingNodes(nodes)
                                // otherwise, the node is referenced from some resolved non-conflicted node
                                // => it should be kept with all children (avoid unregistering)
                            }.filterIsInstance<DependencyNodeWithContext>()
                        }
                }

        nodesToUnregister.forEach { node ->
            conflictDetectionMutexByKey.withLock(node.key.hashCode()) {
                val similarNodes = similarNodesByKey.computeIfAbsent(node.key) { mutableSetOf() }
                similarNodes.remove(node)
                unspecifiedVersionHelper?.unregisterNode(node)
            }
        }
    }

    private fun DependencyNode.isOrphanChildOfConflictingNodes(
        nodes: Map<DependencyNodeWithContext, List<DependencyNodeWithContext>>,
    ): Boolean =
        // there is the single parent that is to be unregistered
        // => the child node can be unregistered as well with all children (except those referenced outside)
        parents.size == 1
                // all parents lead to one of the unregistered top nodes
                // => the node could be unregistered as well with all children
                || !isThereAPathToTopBypassing(nodes.keys)

    private fun DependencyNode.isThereAPathToTopBypassing(nodes: Set<DependencyNodeWithContext>): Boolean {
        if (parents.isEmpty()) return true // we reach the root

        val nonBypassedParents = parents - nodes
        return nonBypassedParents.any { it.isThereAPathToTopBypassing(nodes) }
    }

    /**
     * Registers this node for potential conflict resolution and returns whether it already conflicts with a previously
     * seen node. Can be called concurrently with any node, including those sharing the same key.
     */
    suspend fun registerAndDetectConflicts(node: DependencyNodeWithContext): Boolean =
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
    suspend fun resolveConflicts(): Set<DependencyNodeWithContext> = coroutineScope {
        conflictingNodes()
            .map { candidates ->
                async {
                    val candidatesWithOldChildren = candidates.associateWith { it.children }
                    val resolved = candidates.resolveConflict()
                    if (resolved) {
                        candidatesWithOldChildren
                    } else {
                        emptyMap()
                    }
                }
            }
            .awaitAll()
            .fold(emptyMap<DependencyNodeWithContext,List<DependencyNodeWithContext>>()) { acc, map ->  acc + map }
            .also {
                unregisterOrphanNodes(it)
                conflictedKeys.clear()
            }
            .let {
                it.keys + (unspecifiedVersionHelper?.resolveVersions() ?: emptyList())
            }
    }

    private fun conflictingNodes(): List<Set<DependencyNodeWithContext>> = conflictedKeys.map { key ->
        similarNodesByKey[key] ?: throw AmperDependencyResolutionException("Nodes are missing for ${key.name}")
    }

    private fun Collection<DependencyNodeWithContext>.resolveConflict(): Boolean {
        val strategy = conflictResolutionStrategies.find { it.isApplicableFor(this) && it.seesConflictsIn(this) }
            ?: return true // if no strategy sees the conflict, there is no conflict, so it is considered resolved
        return strategy.resolveConflictsIn(this)
    }
}

private class UnspecifiedMavenDependencyVersionHelper(val unspecifiedVersionProvider: UnspecifiedVersionResolver<MavenDependencyNodeWithContext>) {
    private val unversionedNodesByKey = ConcurrentHashMap<Key<*>, MutableSet<MavenDependencyNodeWithContext>>()

    fun registerNode(node: DependencyNodeWithContext) = doIfApplicable(node) {
        val unversionedNodes = unversionedNodesByKey.computeIfAbsent(node.key) { mutableSetOf() }
        unversionedNodes.add(it)
    }

    fun unregisterNode(node: DependencyNodeWithContext) = doIfApplicable(node) {
        val unversionedNodes = unversionedNodesByKey.computeIfAbsent(node.key) { mutableSetOf() }
        unversionedNodes.remove(it)
    }

    private fun doIfApplicable(node: DependencyNodeWithContext, block: (MavenDependencyNodeWithContext) -> Unit) {
        if (node is MavenDependencyNodeWithContext && node.resolvedVersion() == null && unspecifiedVersionProvider.isApplicable(node)) {
            block(node)
        }
    }

    /**
     * Try to resolve unspecified dependency versions.
     *
     * @return successfully resolved unspecified versions
     */
    fun resolveVersions(): List<MavenDependencyNodeWithContext> {
        return unversionedNodesByKey.values.flatMap { nodes ->
            val resolvedNodes = unspecifiedVersionProvider.resolveVersions(nodes)

            resolvedNodes.forEach { (node, resolvedVersion) ->
                node.dependency = node.context.createOrReuseDependency(node.dependency.coordinates.copy(version = resolvedVersion))
                node.versionFromBom = resolvedVersion
                nodes.remove(node)
            }

            resolvedNodes.keys
        }
    }
}

/**
 * It has the following properties (in addition to [DependencyNode]).
 *
 * - Holds a context relevant for it and its children.
 * - Has mutable state, children, and messages that could change as a result of the resolution process.
 *
 * By the resolution process we mean finding the node's dependencies (children) according to provided context,
 * namely, a [ResolutionScope] and a set of [ResolutionPlatform]s.
 */
interface DependencyNodeWithContext: DependencyNode {

    override val parents: Set<DependencyNode> get() = context.nodeParents
    val context: Context
    override val children: List<DependencyNodeWithContext>
    override val messages: List<Message>

    /**
     * Fills [children], taking [context] into account.
     * In the process, [messages] are populated with relevant information or errors.
     *
     * @return true if the new child nodes need to be resolved themselves recursively, or false if the node was already
     * resolved and the children should be skipped.
     *
     * @see ResolutionState
     * @see org.jetbrains.amper.dependency.resolution.diagnostics.Message
     * @see org.jetbrains.amper.dependency.resolution.diagnostics.Severity
     */
    suspend fun resolveChildren(level: ResolutionLevel, transitive: Boolean)

    /**
     * Ensures that the dependency-relevant files are on disk, according to settings.
     */
    suspend fun downloadDependencies(downloadSources: Boolean = false)

    /**
     * Key that identifies this [DependencyNodeWithContext] in the cache used for Dependency Resolution.
     * All parameters that affect the resolution of this node should be taken into account
     * while computing the cache entry key.
     *
     * For instance, ktor.io:ktor-client-core:1.6.7 resolved for jvm platform
     * is different from ktor.io:ktor-client-core:1.6.7 resolved for iosX64
     *
     * This key might be used for storing this particular node in the cache alone.
     * In this case, [DependencyNodeWithContext.cacheEntryKey] all transitive children together
     * represent configuration of such an entry.
     *
     * If the node configuration changes, then the cache entry related to this node is recalculated.
     *
     * @see [org.jetbrains.amper.incrementalcache.IncrementalCache.execute] for details
     */
    val cacheEntryKey: CacheEntryKey
}

/**
 * A mutex to protect the resolution of a node.
 * This prevents two jobs from resolving the same node (and children), while
 * still allowing to spawn multiple jobs for the same node (which happens in the case of diamonds).
 *
 * Why multiple jobs per node? Why not reuse a single job? The nodes are a graph, while structured concurrency is a tree
 * of jobs. We want to be able to cancel a subgraph of jobs without caring about whether another non-canceled parent
 * requires one of the child dependencies: this parent just launches its own job for the node, and that one is not canceled.
 */
// TODO this should probably be an internal property of the dependency node instead of being stored in the nodeCache
private val DependencyNodeWithContext.resolutionMutex: Mutex
    get() = context.nodeCache.computeIfAbsent(Key<Mutex>("resolutionMutex")) { Mutex() }

/**
 * The thread-safe list of coroutine [Job]s currently resolving this node.
 *
 * We use a copy-on-write array list here so that we can iterate it safely for cancellation, although the
 * cancellation itself ultimately modifies the list (removes terminated jobs).
 */
// TODO this should probably be an internal property of the dependency node instead of being stored in the nodeCache
private val DependencyNodeWithContext.resolutionJobs: MutableList<Job>
    get() = context.nodeCache.computeIfAbsent(Key<MutableList<Job>>("resolutionJobs")) { CopyOnWriteArrayList() }

class ResolvedGraph(
    val root: DependencyNode,
    val expirationTime: Instant?
)

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

sealed class CacheEntryKey {
    object NotCached: CacheEntryKey() {
        override fun computeKey() = null
    }

    data class CompositeCacheEntryKey(val components: List<Any>): CacheEntryKey() {
        override fun computeKey(): String {
            return components.joinToString(",")
        }
    }

    abstract fun computeKey(): String?

    companion object {
        fun fromString(value: String) = CompositeCacheEntryKey(listOf(value))
    }
}

enum class IncrementalCacheUsage {
    SKIP,
    USE,
    REFRESH_AND_USE,
}

/**
 * @return [DependencyNodeWithContext.cacheEntryKey] if this node and all its parents
 * are associated with the same [ResolutionConfig],
 * otherwise composite key containing components of the original key plus node's [ResolutionConfig] is returned.
 */
internal fun DependencyNodeWithContext.getParentAwareCacheEntryKey(): CacheEntryKey {
    val node= this
    val cacheEntryKey = when (val cacheEntryKey = node.cacheEntryKey) {
        is CacheEntryKey.NotCached -> CacheEntryKey.NotCached
        is CacheEntryKey.CompositeCacheEntryKey -> {
            if (node is RootDependencyNode)
                cacheEntryKey
            else {
                val skipContext = node.parents.isNotEmpty() && node.parents.all {
                    (it is DependencyNodeWithContext)
                            && ResolutionConfigPlain(node.context.settings) == ResolutionConfigPlain(it.context.settings)
                }
                if (skipContext)
                    cacheEntryKey
                else
                    cacheEntryKey.copy(
                        components = cacheEntryKey.components + listOf(
                            ResolutionConfigPlain(node.context.settings)
                        )
                    )
            }
        }
    }

    return cacheEntryKey
}

// todo (AB) : Check all places where this exception is thrown,
// todo (AB) : it might be better to convert it either to diagnostic or to error.
class AmperDependencyResolutionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)