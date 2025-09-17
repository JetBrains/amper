/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.amper.dependency.resolution.diagnostics.Message

interface DependencyNodeHolder: DependencyNode {
    val name: String
}

/**
 * Serves as a higher level holder for other dependency nodes.
 * It's statically defined, thus always resolved and has NO-OP implementations of the interface methods.
 * Its name must be unique to distinguish it from other `ModuleDependencyNode`s.
 * 
 * It's the responsibility of the caller to set a parent for this node if none was provided via the constructor.
 * 
 * @see [MavenDependencyNode]
 */
abstract class DependencyNodeHolderImpl(
    override val name: String,
    final override val children: List<DependencyNodeWithResolutionContext>,
    templateContext: Context,
    parentNodes: Set<DependencyNodeWithResolutionContext> = emptySet(),
) : DependencyNodeWithResolutionContext, DependencyNodeHolder {

    init {
        children.forEach { it.context.nodeParents.add(this) }
    }

    override val context: Context = templateContext.copyWithNewNodeCache(parentNodes)
    override val key: Key<*> = Key<DependencyNodeHolderImpl>(name)
    override val messages: List<Message> = listOf()

    override fun toString(): String = name

    override suspend fun resolveChildren(level: ResolutionLevel, transitive: Boolean) {}

    override suspend fun downloadDependencies(downloadSources: Boolean) {}
}

@Serializable
abstract class DependencyNodeHolderPlainBase(
    @Transient
    private val graphContext: DependencyGraphContext = currentGraphContext()
): DependencyNodeHolder, DependencyNodePlainBase(graphContext) {

}

class RootDependencyNodeInput(
    name: String = "root",
    /**
     * // todo (AB) : Add a better comment here
     * This field might be used as an ID of cache entry of resolution graph.
     * Passing the same ID with different graphs will lead to invalidating the cache entry on every single run, reducing the cache usage to minimum.
     * This id should uniquely identify a resolution operation that is going to be cached.
     */
    val resolutionId: String? = null,
    children: List<DependencyNodeWithResolutionContext>,
    templateContext: Context,
    parentNodes: Set<DependencyNodeWithResolutionContext> = emptySet(),
) : RootDependencyNode,
    DependencyNodeHolderImpl(
        name, children, templateContext, parentNodes = parentNodes)


class RootDependencyNodeStub(
    override val name: String = "root",
    override val children: List<DependencyNode> = emptyList(),
    override val parents: Set<DependencyNode> = emptySet(),
): RootDependencyNode {
    override val key: Key<*> = Key<DependencyNodeHolderImpl>(name)
    override val messages = emptyList<Message>()

    override fun toString() = name
}

interface RootDependencyNode: DependencyNodeHolder {
    override fun toEmptyNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain =
        RootDependencyNodePlain(name, graphContext = graphContext)
}

@Serializable
class RootDependencyNodePlain internal constructor(
    override val name: String,
    override val childrenRefs: List<DependencyNodeReference> = mutableListOf(),
    @Transient
    private val graphContext: DependencyGraphContext = currentGraphContext(),
): RootDependencyNode, DependencyNodeHolderPlainBase(graphContext) {
    override val parentsRefs = mutableSetOf<DependencyNodeReference>()

    @Transient
    override val key: Key<*> = Key<DependencyNodeHolderImpl>(name)
    override val messages: List<Message> = listOf()

    override fun toString() = name
}
