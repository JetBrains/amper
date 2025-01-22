/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlin.collections.forEach
import kotlin.collections.plus

class DependencyNodeWithChildren(internal val node: DependencyNode): DependencyNode {
    override val children: MutableList<DependencyNode> = mutableListOf()
    override val context: Context = node.context
    override val key: Key<*> = node.key
    override val messages: List<Message> = node.messages.toMutableList()
    override fun toString() = node.toString()

    override suspend fun resolveChildren(evel: ResolutionLevel, transitive: Boolean) = throw UnsupportedOperationException("Can't run resolution on wrapper")
    override suspend fun downloadDependencies(downloadSources: Boolean) = throw UnsupportedOperationException("Can't download dependencies for wrapper")
}

/**
 * Returned filtered dependencies graph,
 * containing paths from the root to the maven dependency node corresponding to the given coordinates (group and module)
 * and having the version equal to the actual resolved version of this dependency in the graph.
 * If the resolved dependency version is enforced by constraint, then the path to that constraint is presented
 * in the returned graph together with paths to all versions of this dependency.
 *
 * Every node of the returned graph is of the type [DependencyNodeWithChildren] holding the corresponding node of the original graph inside.
 */
fun filterGraph(group: String, module: String, graph: DependencyNode): DependencyNode {
    val nodes = graph.distinctBfsSequence()
        .filter {
            it is MavenDependencyNode &&  it.group == group && it.module == module
                    || it is MavenDependencyConstraintNode && it.group == group && it.module == module
        }
        .toSet()

    if (nodes.isEmpty()) return DependencyNodeWithChildren(graph) // root node without children

    val nodesWithDecisiveParents = mutableSetOf<DependencyNode>()
    nodes.addDecisiveParents(nodesWithDecisiveParents, graph)

    val filteredGraph = graph.withFilteredChildren { it in nodesWithDecisiveParents }
    return filteredGraph
}

/**
 * It takes a collection of nodes and calculates all intermediate nodes up to the root
 * that belong to the path from the root node to the node from the original collection.
 *
 * It takes none-overridden nodes only if possible
 * (as soon as a dependency node is overridden,
 * it is no longer possible to say whether its actual overridden children were requested by the original version of the dependency or not)
 *
 * On the first step the given collection is filtered the following way:
 * - nodes of the given collection that are neither maven dependency, nor constraints are added to the resulting set unconditionally
 * - if the given collection contains non-overridden dependency nodes, then those are added to the resulting set,
 *   and the method is called for parents of the resulting set nodes.
 * - if all dependency nodes from the given collection are overridden,
 *   and there is a constraint that caused that (either in a given list or somewhere else in a graph),
 *   then all dependency nodes together with effective constraint are added to the resulting set,
 *   and the method is called for parents of the resulting set nodes.
 */
private fun Set<DependencyNode>.addDecisiveParents(nodesWithDecisiveParents: MutableSet<DependencyNode>, graph: DependencyNode) {
    val allDependenciesAndConstraints = filter { it is MavenDependencyNode || it is MavenDependencyConstraintNode }.toSet()
    val noneFilterableNodes = this - allDependenciesAndConstraints

    val nodes = if (allDependenciesAndConstraints.isEmpty()) {
        emptySet()
    } else {
        val groupedByCoordinates = allDependenciesAndConstraints.groupBy {
            when (it) {
                is MavenDependencyNode -> it.group to it.module
                is MavenDependencyConstraintNode -> it.group to it.module
                else -> error("unexpected node type ${it::class.java.simpleName}")
            }
        }.toMap()

        groupedByCoordinates.flatMap { entry ->
            val (group, module) = entry.key
            val dependenciesAndConstraints = entry.value

            val effectiveNodes = dependenciesAndConstraints.filter {
                it is MavenDependencyNode && it.version == it.dependency.version
                        || it is MavenDependencyConstraintNode && it.version == it.dependencyConstraint.version
            }.toSet()

            val dependencies = dependenciesAndConstraints.filterIsInstance<MavenDependencyNode>()
            if (effectiveNodes.isEmpty()) {
                val constraints = dependenciesAndConstraints
                    .mapNotNull { node ->
                        val overriddenBy = when (node) {
                            is MavenDependencyNode -> node.overriddenBy
                            is MavenDependencyConstraintNode -> node.overriddenBy
                            else -> null
                        }
                        overriddenBy
                            ?.filterIsInstance<MavenDependencyConstraintNode>()
                            ?.filter {
                                it.group == group && it.module == module
                                        && it.version == it.dependencyConstraint.version
                                        && node.resolvedVersion() == it.resolvedVersion()
                            }
                    }.flatMap { it }
                    .distinct()

                (dependencies + constraints).toSet()
            } else {
                if (effectiveNodes.any { it is MavenDependencyNode }) {
                    // Constraints are redundant
                    effectiveNodes.filterIsInstance<MavenDependencyNode>().toSet()
                } else {
                    // constraints only => take both dependencies and constraints
                    (dependencies + effectiveNodes).toSet()
                }
            }
        }
    } + noneFilterableNodes

    nodesWithDecisiveParents.addAll(nodes)

    nodes.forEach {
        // todo (AB) : Some parents might be obsolete (unreachable from the root) in case those are left from canceled conflicting subgraph
        it.parents.toSet().addDecisiveParents(nodesWithDecisiveParents, graph)
    }
}

/**
 * Returned filtered dependencies graph.
 * Given filter is applied transitively to the children of all the nodes of the original graph.
 *
 * Every node of the returned graph is of the type [DependencyNodeWithChildren] holding the original node inside.
 */
private fun DependencyNode.withFilteredChildren(
    cachedChildrenMap: MutableMap<DependencyNode, DependencyNodeWithChildren> = mutableMapOf(),
    childrenFilter: (DependencyNode) -> Boolean
): DependencyNodeWithChildren {
    val filtered = if (!cachedChildrenMap.containsKey(this)) {
        val nodeWithFilteredChildren = DependencyNodeWithChildren(this)
        cachedChildrenMap.put(this, nodeWithFilteredChildren)
        val children = this.children.filter(childrenFilter).map {
            it.withFilteredChildren(cachedChildrenMap, childrenFilter)
        }
        nodeWithFilteredChildren.children.addAll(children)
        nodeWithFilteredChildren
    } else {
        cachedChildrenMap[this]!!
    }
    return filtered
}