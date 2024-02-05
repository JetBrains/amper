/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

class ModuleDependencyNode(
    templateContext: Context,
    val name: String,
    override val children: List<DependencyNode>,
    parentNode: DependencyNode? = null,
) : DependencyNode {

    init {
        children.forEach { it.context.nodeCache[parentNodeKey] = this }
    }

    override val context: Context = templateContext.copyWithNewNodeCache(parentNode)
    override val key: Key<*> = Key<ModuleDependencyNode>(name)
    override val state: ResolutionState = ResolutionState.RESOLVED
    override val messages: List<Message> = listOf()

    override fun toString(): String = name

    override fun resolve(level: ResolutionLevel) {}

    override fun downloadDependencies() {}
}
