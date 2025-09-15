/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.amper.dependency.resolution.diagnostics.Message

/**
 * Serves as a higher level holder for other dependency nodes.
 * It's statically defined, thus always resolved and has NO-OP implementations of the interface methods.
 * Its name must be unique to distinguish it from other `ModuleDependencyNode`s.
 * 
 * It's the responsibility of the caller to set a parent for this node if none was provided via the constructor.
 * 
 * @see [MavenDependencyNode]
 */
abstract class DependencyNodeHolder(
    val name: String,
    final override val children: List<DependencyNodeWithResolutionContext>,
    templateContext: Context,
    parentNodes: Set<DependencyNodeWithResolutionContext> = emptySet(),
) : DependencyNodeWithResolutionContext {

    init {
        children.forEach { it.context.nodeParents.add(this) }
    }

    override val context: Context = templateContext.copyWithNewNodeCache(parentNodes)
    override val key: Key<*> = Key<DependencyNodeHolder>(name)
    override val messages: List<Message> = listOf()

    override fun toString(): String = name

    override suspend fun resolveChildren(level: ResolutionLevel, transitive: Boolean) {}

    override suspend fun downloadDependencies(downloadSources: Boolean) {}

    // todo (AB) : Implement children as well
    override fun toSerializableReference(graphContext: DependencyGraphContext, parent: DependencyNodeReference?): DependencyNodeReference {
        return graphContext.getDependencyNodeReferenceAndSetParent(this, parent)
            ?: run {
                // 1. Create an empty reference first (to break cycles)
                val newNodePlain = toEmptyNodePlain(graphContext)

                // 2. register empty reference (to break cycles)
                val newReference = graphContext.registerDependencyNodePlainWithParent(this, newNodePlain, parent)

                // 3. enrich it with references
                fillEmptyNodePlain(newNodePlain, graphContext, newReference)

                newReference
            }
    }

    abstract fun toEmptyNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain

    open fun fillEmptyNodePlain(nodePlain: DependencyNodePlain, graphContext: DependencyGraphContext, nodeReference: DependencyNodeReference?) {
        val children = children.map { it.toSerializableReference(graphContext, nodeReference) }

        if (children.isNotEmpty()) {
            (nodePlain.childrenRefs as MutableList<DependencyNodeReference>).addAll(children)
        }
    }
}

interface DependencyNodeHolderPlain: DependencyNodePlain

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
) : DependencyNodeHolder(name, children, templateContext, parentNodes = parentNodes), RootDependencyNode {
    override fun toEmptyNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain =
        RootDependencyNodePlain(name, graphContext = graphContext)
}

class RootDependencyNodeStub(
    override val name: String = "root",
    override val children: List<DependencyNode> = emptyList(),
    override val parents: Set<DependencyNode> = emptySet(),
): RootDependencyNode {
    override val key: Key<*> = Key<DependencyNodeHolder>(name)
    override val messages = emptyList<Message>()
    override fun toSerializableReference(graphContext: DependencyGraphContext, parent: DependencyNodeReference?): DependencyNodeReference = error("Unsupported, sub node id node intended to be serializable")

    override fun toString() = name
}

interface RootDependencyNode: DependencyNode {
    val name: String
}

@Serializable
class RootDependencyNodePlain internal constructor(
    override val name: String,
    override val childrenRefs: List<DependencyNodeReference> = mutableListOf(),
    @Transient
    private val graphContext: DependencyGraphContext = currentGraphContext(),
): DependencyNodeHolderPlain, RootDependencyNode {
    override val parentsRefs = mutableSetOf<DependencyNodeReference>()
    override val parents = mutableSetOf<DependencyNode>()
    override val children: List<DependencyNode> by lazy { childrenRefs.map { it.toNodePlain(graphContext) } }

    @Transient
    override val key: Key<*> = Key<DependencyNodeHolder>(name)
    override val messages: List<Message> = listOf()

    override fun toString() = name
}
