/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

class ModuleDependencyNode(
    override val context: Context,
    val name: String,
    override val children: List<DependencyNode>,
) : DependencyNode {

    override val key: Key<*> = Key<ModuleDependencyNode>(name)
    override var state: ResolutionState = ResolutionState.RESOLVED
    override var level: ResolutionLevel = ResolutionLevel.CREATED
    override val messages: List<Message> = listOf()

    override fun toString(): String = name

    override fun resolve(level: ResolutionLevel) {}

    override fun downloadDependencies() {}
}
