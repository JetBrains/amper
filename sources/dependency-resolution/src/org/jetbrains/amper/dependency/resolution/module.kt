/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

class ModuleDependencyNode(
    val name: String,
    override val children: Collection<DependencyNode>,
) : DependencyNode {

    override var state: ResolutionState = ResolutionState.RESOLVED
    override var level: ResolutionLevel = ResolutionLevel.CREATED
    override val messages: Collection<Message> = listOf()

    override fun toString(): String = name

    override fun resolve(level: ResolutionLevel) {}

    override fun downloadDependencies() {}
}
