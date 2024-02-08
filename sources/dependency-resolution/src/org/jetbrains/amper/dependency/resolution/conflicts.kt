/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.apache.maven.artifact.versioning.ComparableVersion

/**
 * Defines a conflict on a group of nodes with the same key and provides a way to resolve it.
 * Several strategies can work alongside, but only the first one is used for the conflict resolution.
 */
interface ConflictResolutionStrategy {

    /**
     * @return `true` if the strategy is able to find and resolve conflicts for the provided [candidates].
     * They are guaranteed to have the same [Key].
     */
    fun isApplicableFor(candidates: List<DependencyNode>): Boolean

    /**
     * @return `true` if [candidates] have conflicts among them.
     */
    fun seesConflictsIn(candidates: List<DependencyNode>): Boolean

    /**
     * Resolves conflicts among [candidates] by changing their state and returns `true` on success.
     * Returning `false` immediately interrupts the resolution process.
     */
    fun resolveConflictsIn(candidates: List<DependencyNode>): Boolean
}

/**
 * Upgrades all dependencies to the highest version among them.
 * Works only with [MavenDependencyNode]s.
 */
class HighestVersionStrategy : ConflictResolutionStrategy {

    /**
     * @return `true` if all candidates are [MavenDependencyNode]s.
     */
    override fun isApplicableFor(candidates: List<DependencyNode>): Boolean =
        candidates.all { it is MavenDependencyNode }

    /**
     * @return `true` if dependencies have different versions according to [ComparableVersion].
     */
    override fun seesConflictsIn(candidates: List<DependencyNode>): Boolean =
        candidates.asSequence()
            .map { it as MavenDependencyNode }
            .map { it.dependency.version }
            .distinct()
            .distinctBy { ComparableVersion(it) }
            .take(2)
            .count() > 1

    /**
     * Sets [MavenDependency] with the highest version and state to all candidates. Never fails.
     *
     * @return always `true`
     */
    override fun resolveConflictsIn(candidates: List<DependencyNode>): Boolean {
        val dependency = candidates.asSequence()
            .map { it as MavenDependencyNode }
            .map { it.dependency }
            .distinctBy { it.version }
            .maxBy { ComparableVersion(it.version) }
        candidates.asSequence().map { it as MavenDependencyNode }.forEach { it.dependency = dependency }
        return true
    }
}
