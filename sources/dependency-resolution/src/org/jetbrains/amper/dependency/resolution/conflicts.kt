/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.apache.maven.artifact.versioning.ComparableVersion

interface ConflictResolutionStrategy {
    fun isApplicableFor(candidates: List<DependencyNode>): Boolean
    fun seesConflictsIn(candidates: List<DependencyNode>): Boolean
    fun resolveConflictsIn(candidates: List<DependencyNode>): Boolean
}

class HighestVersionStrategy : ConflictResolutionStrategy {

    override fun isApplicableFor(candidates: List<DependencyNode>): Boolean =
        candidates.all { it is MavenDependencyNode }

    override fun seesConflictsIn(candidates: List<DependencyNode>): Boolean =
        candidates.map { it as MavenDependencyNode }
            .map { it.dependency }
            .distinctBy { ComparableVersion(it.version) }
            .size > 1

    override fun resolveConflictsIn(candidates: List<DependencyNode>): Boolean {
        val mavenDependencyNodes = candidates.map { it as MavenDependencyNode }
        val dependency = mavenDependencyNodes.map { it.dependency }.maxWith(
            compareBy<MavenDependency> { ComparableVersion(it.version) }.thenBy { it.state }
        )
        mavenDependencyNodes.forEach { it.dependency = dependency }
        return true
    }
}
