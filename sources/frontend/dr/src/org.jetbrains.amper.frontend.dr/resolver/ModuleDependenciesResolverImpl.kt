/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.plus
import org.jetbrains.amper.dependency.resolution.DependencyGraph
import org.jetbrains.amper.dependency.resolution.DependencyGraph.Companion.toGraph
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolderImpl
import org.jetbrains.amper.dependency.resolution.DependencyNodePlain
import org.jetbrains.amper.dependency.resolution.DependencyNodeWithResolutionContext
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenDependencyConstraintNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyConstraintNodePlain
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNodeImpl
import org.jetbrains.amper.dependency.resolution.MavenDependencyNodePlain
import org.jetbrains.amper.dependency.resolution.ResolutionConfig
import org.jetbrains.amper.dependency.resolution.ResolutionLevel
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeInput
import org.jetbrains.amper.dependency.resolution.RootDependencyNodePlain
import org.jetbrains.amper.dependency.resolution.SpanBuilderSource
import org.jetbrains.amper.dependency.resolution.UnspecifiedVersionResolver
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.registerSerializableMessages
import org.jetbrains.amper.dependency.resolution.filterGraph
import org.jetbrains.amper.dependency.resolution.originalVersion
import org.jetbrains.amper.dependency.resolution.spanBuilder
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.DependencyCoordinatesInGradleFormat
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.MavenClassifiersAreNotSupported
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.MavenCoordinatesHaveLineBreak
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.MavenCoordinatesHavePartEndingWithDot
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.MavenCoordinatesHaveSlash
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.MavenCoordinatesHaveSpace
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.MavenCoordinatesHaveTooFewParts
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.MavenCoordinatesHaveTooManyParts
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.MavenCoordinatesShouldBuildValidPath
import org.jetbrains.amper.frontend.dr.resolver.flow.Classpath
import org.jetbrains.amper.frontend.dr.resolver.flow.IdeSync
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import kotlin.io.path.pathString
import kotlin.reflect.KClass

private val logger = LoggerFactory.getLogger(ModuleDependenciesResolverImpl::class.java)

internal class ModuleDependenciesResolverImpl: ModuleDependenciesResolver {

    override fun AmperModule.resolveDependenciesGraph(
        dependenciesFlowType: DependenciesFlowType,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        spanBuilder: SpanBuilderSource,
    ): ModuleDependencyNodeWithModule {
        val resolutionFlow = when (dependenciesFlowType) {
            is DependenciesFlowType.ClassPathType -> Classpath(dependenciesFlowType)
            is DependenciesFlowType.IdeSyncType -> IdeSync(dependenciesFlowType)
        }

        return resolutionFlow.directDependenciesGraph(this, fileCacheBuilder, spanBuilder)
    }

    internal fun DependencyNodeWithResolutionContext.getDependenciesGraphInput(): Map<String, List<String>> {
        val uniqueDependencies = buildMap { fillDependenciesGraphInput(this) }
        return uniqueDependencies
    }

    private fun DependencyNodeWithResolutionContext.fillDependenciesGraphInput(
        dependenciesMap: MutableMap<String, List<String>>
    ) {
        // todo (AB) : Provide straightforward Id + graph input

        children.forEach {
            if (it is ModuleDependencyNodeWithModule) {
                if (it.children.isEmpty()) return@forEach

                val firstChildPlatforms = it.context.settings.platforms
                val hasDepsWithAnotherPlatformsSet = it.children.any { child ->
                    child.context.settings.platforms != firstChildPlatforms
                }

                if (hasDepsWithAnotherPlatformsSet) {
                    val groupedByFragment = it.children.groupBy {
                        it as DirectFragmentDependencyNodeHolder
                        "${it.fragment.module.userReadableName}:${it.fragment.name}"
                    }
                    groupedByFragment.forEach {
                        val resolutionScopeKey = it.key + "," + it.value[0].context.settings.key()
                        dependenciesMap[resolutionScopeKey] =
                            it.value.mapNotNull{ (it as DirectFragmentDependencyNodeHolder).toDependencyResolutionKey() }
                    }
                } else {
                    val coordinates = it
                        .distinctBfsSequence()
                        .filterIsInstance<DirectFragmentDependencyNodeHolder>()
                        .distinctBy { it.dependencyNode }
                        .mapNotNull { it.toDependencyResolutionKey() }.toList()
                    dependenciesMap[it.graphEntryName + it.context.settings.key()] = coordinates
                }
            } else if (it is DependencyNodeHolderImpl) {
                it.fillDependenciesGraphInput(dependenciesMap)
            }
        }
    }

    private fun DirectFragmentDependencyNodeHolder.toDependencyResolutionKey(): String? =
        (this.dependencyNode as? MavenDependencyNode)
            ?.getOriginalMavenCoordinates()
            ?.toString()

    override suspend fun DependencyNodeHolderImpl.resolveDependencies(
        resolutionDepth: ResolutionDepth,
        resolutionLevel: ResolutionLevel,
        downloadSources: Boolean,
        incrementalCacheUsage: IncrementalCacheUsage
    ): DependencyNode {
        return context.spanBuilder("DR.graph:resolveDependencies").use {
            when (resolutionDepth) {
                ResolutionDepth.GRAPH_ONLY -> {
                    /* Do nothing, graph is already given */
                    this@resolveDependencies
                }

                ResolutionDepth.GRAPH_WITH_DIRECT_DEPENDENCIES,
                ResolutionDepth.GRAPH_FULL,
                    -> {
                    val resolutionId = when (this@resolveDependencies) {
                        is RootDependencyNodeInput -> {
                            this@resolveDependencies.resolutionId?.let {
                                it + (if (resolutionDepth == ResolutionDepth.GRAPH_WITH_DIRECT_DEPENDENCIES) "[direct only]" else "") +
                                        "[withSources=$downloadSources]"
                            }
                        }
                        else -> null
                    }?.replaceTheEndWithMd5IfTooLong()

                    if (resolutionId == null || incrementalCacheUsage == IncrementalCacheUsage.SKIP) {
                        resolveDependencies(resolutionLevel, resolutionDepth, downloadSources)
                        this@resolveDependencies
                    } else {
                        var graphResolvedInsideCache: DependencyNode? = null
                        // todo (AB): It should be adopted and moved into dependency-resolution library
                        // todo (AB): ResolveExternalDependencies task already wraps DR into incremental cache, that should be removed as well
                        val executeOnChangedInputs = ExecuteOnChangedInputs(
                            // todo (AB) : It should point into project build dir instead of common DR root
                            stateRoot = context.settings.fileCache.amperCache.resolve("m2.incremental.state"),
                            codeVersion = "2",
                            spanBuilder = context.settings.spanBuilder
                        )

                        val configuration = mapOf(
                            "userCacheRoot" to context.settings.fileCache.amperCache.pathString,
                            "dependencies" to getDependenciesGraphInput().entries.joinToString("|") {
                                "${it.key},${it.value.map { it.toString() }.sorted().joinToString(",")}"
                            },
                        )

                        val resolvedGraph = try {
                            executeOnChangedInputs.execute(
                                // todo (AB): Think about id, it should specify graph all logical resolution scopes.
                                id = resolutionId,
                                configuration,
                                forceRecalculation = (incrementalCacheUsage == IncrementalCacheUsage.REFRESH_AND_USE),
                                inputs = listOf(),
                            ) {
                                context.spanBuilder("DR.graph:resolution")
                                    .setAttribute("configuration", configuration["dependencies"]) // todo (AB) : Remove it (was added for debugging purposes))
                                    .setAttribute("userCacheRoot", configuration["userCacheRoot"]) // todo (AB) : Remove it (was added for debugging purposes))
                                    .setAttribute("resolutionId", resolutionId) // todo (AB) : Remove it (was added for debugging purposes))
                                    .use {
                                        resolveDependencies(resolutionLevel, resolutionDepth, downloadSources)
                                        graphResolvedInsideCache = this@resolveDependencies


                                        val serializableGraph = graphResolvedInsideCache.toGraph()
                                        val serialized = json.encodeToString(serializableGraph)
                                        ExecuteOnChangedInputs.ExecutionResult(
                                            graphResolvedInsideCache.dependencyPaths(),
                                            mapOf("graph" to serialized)
                                        )
                                    }
                            }.let {
                                if (graphResolvedInsideCache != null) {
                                    graphResolvedInsideCache
                                } else {
                                    val serialized = it.outputProperties["graph"]!!
                                    val deserializedGraph = context.spanBuilder("DR.graph:deserialization").use {
                                        json.decodeFromString<DependencyGraph>(serialized)
                                    }
                                    val resolvedGraph = deserializedGraph.root.toNodePlain(deserializedGraph.graphContext)

                                    // Merge the input graph (that has PSI references) with the deserialized one
                                    resolvedGraph.fillNotation(this@resolveDependencies)

                                    resolvedGraph
                                }
                            }
                        } catch (e: Exception) {
                            logger.warn("Unable to get dependency graph from incremental cache, " +
                                    "falling back to non-cached resolution: ${e.toString()}")
                            if (graphResolvedInsideCache != null) {
                                graphResolvedInsideCache
                            } else {
                                // todo (AB) : Invalidate cache entry manually on deserialization failure
                                // todo (AB) : (might be not needed though if cache.codeVersion is properly specified)

                                // Graph was taken from the cache, but deserialization failed.
                                // Fallback to non-cached resolution
                                resolveDependencies(resolutionLevel, resolutionDepth, downloadSources)
                                this@resolveDependencies
                            }
                        }

                        resolvedGraph
                    }
                }
            }
        }
    }

    private fun DependencyNodePlain.fillNotation(sourceNode: DependencyNodeHolderImpl) {
        val sourceDirectDeps = sourceNode.children.groupBy { it.key }
        this.children.forEach { node ->
            when (node) {
                is DirectFragmentDependencyNodeHolderPlain -> {
                    val sourceNode = sourceDirectDeps[node.key].resolveCorrespondingSourceNode<DirectFragmentDependencyNodeHolder>(node) {
                        node.notationCoordinates == notation.coordinates.value
                    }
                    node.notation = sourceNode.notation
                }
                is ModuleDependencyNodeWithModulePlain -> {
                    val sourceNode = sourceDirectDeps[node.key].resolveCorrespondingSourceNode<ModuleDependencyNodeWithModule>(node)
                    node.notation = sourceNode.notation
                    node.fillNotation(sourceNode)
                }
                is RootDependencyNodePlain -> {
                    val sourceNode = sourceDirectDeps[node.key].resolveCorrespondingSourceNode<RootDependencyNodeInput>(node)
                    node.fillNotation(sourceNode)
                }
            }
        }
    }

    private inline fun <reified T: DependencyNode> List<DependencyNode>?.resolveCorrespondingSourceNode(
        node: DependencyNodePlain,
        additionalMatch: T.() -> Boolean = { true }
    ): T {
        if (this == null || this.isEmpty())
            error("Deserialized node with key ${node.key} has no corresponding input node")

        this.forEach {
            (it as? T) ?: error(
                "Deserialized node corresponds to unexpected input node of type " +
                        "${this::class.simpleName} while ${node::class.simpleName} is expected"
            )
            if (it.additionalMatch()) return it
        }

        return (this.first() as T)
    }

    private fun String.replaceTheEndWithMd5IfTooLong() = this
        .takeIf { it.length <= 50 }
        ?: (this.substring(0, 50) + md5())

    private suspend fun DependencyNodeHolderImpl.resolveDependencies(
        resolutionLevel: ResolutionLevel,
        resolutionDepth: ResolutionDepth,
        downloadSources: Boolean,
    ) {
        val resolver = Resolver()
        resolver.buildGraph(
            this,
            level = resolutionLevel,
            transitive = resolutionDepth != ResolutionDepth.GRAPH_WITH_DIRECT_DEPENDENCIES,
            unspecifiedVersionResolver = DirectMavenDependencyUnspecifiedVersionResolver()
        )
        resolver.downloadDependencies(this, downloadSources)
    }

    private fun ResolutionConfig.key() = "${scope.name}:${platforms.joinToString(",")}:${repositories.joinToString(",")}"

    override suspend fun AmperModule.resolveDependencies(resolutionInput: ResolutionInput): ModuleDependencyNode {
        with(resolutionInput) {
            val moduleDependenciesGraph = resolveDependenciesGraph(dependenciesFlowType, fileCacheBuilder, spanBuilder)
            val resolvedGraph = moduleDependenciesGraph.resolveDependencies(resolutionDepth, resolutionLevel, downloadSources)
            return resolvedGraph as ModuleDependencyNode
        }
    }

    override fun List<AmperModule>.resolveDependenciesGraph(
        dependenciesFlowType: DependenciesFlowType,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        spanBuilder: SpanBuilderSource,
    ): RootDependencyNodeInput {
        val resolutionFlow = when (dependenciesFlowType) {
            is DependenciesFlowType.ClassPathType -> Classpath(dependenciesFlowType)
            is DependenciesFlowType.IdeSyncType -> IdeSync(dependenciesFlowType)
        }

        return resolutionFlow.directDependenciesGraph(this, fileCacheBuilder, spanBuilder)
    }

    override suspend fun List<AmperModule>.resolveDependencies(resolutionInput: ResolutionInput): DependencyNode {
        return with(resolutionInput) {
            resolutionInput.spanBuilder("DR: Resolving dependencies for the list of modules").use {
                val moduleDependenciesGraph = resolveDependenciesGraph(dependenciesFlowType, fileCacheBuilder, resolutionInput.spanBuilder)
                val resolvedGraph = moduleDependenciesGraph.resolveDependencies(resolutionDepth, resolutionLevel, downloadSources, incrementalCacheUsage)
                resolvedGraph
            }
        }
    }

    override fun dependencyInsight(group: String, module: String, node: DependencyNode, resolvedVersionOnly: Boolean): DependencyNode =
        filterGraph(group, module, node, resolvedVersionOnly)

    override suspend fun AmperModule.dependencyInsight(group: String, module: String, resolutionInput: ResolutionInput): DependencyNode {
        val graph = resolveDependencies(resolutionInput)
        return filterGraph(group, module, graph)
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
            allowStructuredMapKeys = true

            serializersModule = moduleForDependencyNodePlainHierarchy() +
                    moduleForDependencyNodeHierarchy() +
                    moduleMessageHierarchy() /*+
                moduleForDependencyFilePlainHierarchy()*/
        }

        fun moduleForDependencyNodePlainHierarchy() = SerializersModule {
            moduleForDependencyNodeHierarchy(DependencyNodePlain::class as KClass<DependencyNode>)
//        moduleForDependencyNodeHierarchy(DependencyNode::class)
        }

        fun moduleForDependencyNodeHierarchy() = SerializersModule {
            moduleForDependencyNodeHierarchy(DependencyNode::class)
        }

        fun SerializersModuleBuilder.moduleForDependencyNodeHierarchy(kClass: KClass<DependencyNode>) {
            polymorphic(kClass,MavenDependencyNodePlain::class, MavenDependencyNodePlain.serializer())
            polymorphic(kClass,ModuleDependencyNodeWithModulePlain::class, ModuleDependencyNodeWithModulePlain.serializer())
            polymorphic(kClass,RootDependencyNodePlain::class, RootDependencyNodePlain.serializer())
            polymorphic(kClass,DirectFragmentDependencyNodeHolderPlain::class, DirectFragmentDependencyNodeHolderPlain.serializer())
            polymorphic(kClass,MavenDependencyConstraintNodePlain::class, MavenDependencyConstraintNodePlain.serializer())
            polymorphic(kClass,UnresolvedMavenDependencyNodePlain::class,UnresolvedMavenDependencyNodePlain.serializer())
        }

        fun moduleMessageHierarchy() = SerializersModule {
            registerSerializableMessages()
            polymorphic(Message::class,MavenCoordinatesHaveTooFewParts::class, MavenCoordinatesHaveTooFewParts.serializer())
            polymorphic(Message::class,MavenCoordinatesHaveTooManyParts::class, MavenCoordinatesHaveTooManyParts.serializer())
            polymorphic(Message::class,MavenCoordinatesHaveSlash::class, MavenCoordinatesHaveSlash.serializer())
            polymorphic(Message::class,MavenCoordinatesHaveLineBreak::class, MavenCoordinatesHaveLineBreak.serializer())
            polymorphic(Message::class,MavenCoordinatesHaveSpace::class, MavenCoordinatesHaveSpace.serializer())
            polymorphic(Message::class,MavenClassifiersAreNotSupported::class, MavenClassifiersAreNotSupported.serializer())
            polymorphic(Message::class,MavenCoordinatesHavePartEndingWithDot::class, MavenCoordinatesHavePartEndingWithDot.serializer())
            polymorphic(Message::class,MavenCoordinatesShouldBuildValidPath::class, MavenCoordinatesShouldBuildValidPath.serializer())
            polymorphic(Message::class,DependencyCoordinatesInGradleFormat::class, DependencyCoordinatesInGradleFormat.serializer())
        }
    }
}

class DirectMavenDependencyUnspecifiedVersionResolver: UnspecifiedVersionResolver<MavenDependencyNodeImpl> {

    override fun isApplicable(node: MavenDependencyNodeImpl): Boolean {
        return node.originalVersion() == null
    }

    override fun resolveVersions(nodes: Set<MavenDependencyNodeImpl>): Map<MavenDependencyNodeImpl, String> {
        return nodes.mapNotNull { node ->
            if (!isApplicable(node)) {
                null
            } else {
                resolveVersionFromBom(node)?.let { node to it }
            }
        }.toMap()
    }

    /**
     * Resolve an unspecified dependency version from the BOM imported in the same module.
     * Unspecified dependency version of direct dependency should not be taken from transitive dependencies or constraints.
     */
    private fun resolveVersionFromBom(node: MavenDependencyNodeImpl): String? {
        val nodeParentsToFindBomsFrom: List<DirectFragmentDependencyNode> = when {
            // Direct dependency
            node.parents.any { it is DirectFragmentDependencyNode } -> node.parents.filterIsInstance<DirectFragmentDependencyNode>()
            // Transitive dependency,
            // find all direct dependencies this transitive one is referenced by and use those for BOM resolution
            else -> node.fragmentDependencies
        }

        val boms = nodeParentsToFindBomsFrom
            .mapNotNull { it.parents.singleOrNull() as? ModuleDependencyNode }
            .map { node ->
                node.children
                    .filterIsInstance<DirectFragmentDependencyNode>()
                    .map { it.dependencyNode }
                    .filterIsInstance<MavenDependencyNode>()
                    .filter { it.isBom }
            }.flatten()

        boms.forEach { bom ->
            val resolvedVersion = bom.children
                .filterIsInstance<MavenDependencyConstraintNode>()
                .firstOrNull { it.key == node.key }
                ?.originalVersion()

            if (resolvedVersion != null) {
                return resolvedVersion
            }
        }

        return null
    }
}
