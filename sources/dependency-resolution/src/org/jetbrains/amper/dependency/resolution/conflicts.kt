/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Version

/**
 * Defines a conflict on a group of nodes with the same key and provides a way to resolve it.
 * Several strategies can work alongside, but only the first one is used for the conflict resolution.
 */
interface ConflictResolutionStrategy {

    /**
     * @return `true` if the strategy is able to find and resolve conflicts for the provided [candidates].
     * They are guaranteed to have the same [Key].
     */
    fun isApplicableFor(candidates: Collection<DependencyNode>): Boolean

    /**
     * @return `true` if [candidates] have conflicts among them.
     */
    fun seesConflictsIn(candidates: Collection<DependencyNode>): Boolean

    /**
     * Resolves conflicts among [candidates] by changing their state and returns `true` on success.
     * Returning `false` immediately interrupts the resolution process.
     */
    fun resolveConflictsIn(candidates: Collection<DependencyNode>): Boolean
}

/**
 * Upgrades all dependencies to the highest version among them.
 * Works only with [MavenDependencyNode]s.
 */
class HighestVersionStrategy : ConflictResolutionStrategy {

    /**
     * @return `true` if all candidates are [MavenDependencyNode]s.
     */
    override fun isApplicableFor(candidates: Collection<DependencyNode>): Boolean =
        candidates.all { it is MavenDependencyNode || it is MavenDependencyConstraintNode }

    /**
     * @return `true` if dependencies have different versions according to [ComparableVersion].
     */
    override fun seesConflictsIn(candidates: Collection<DependencyNode>): Boolean =
        candidates.asSequence()
            .mapNotNull { it.resolvedVersion() }
            .distinct()
            .distinctBy { ComparableVersion(it) }
            .take(2)
            .count() > 1

    /**
     * Sets [MavenDependency] with the highest version and state to all candidates. Never fails.
     *
     * @return always `true`
     */
    override fun resolveConflictsIn(candidates: Collection<DependencyNode>): Boolean {
        val resolvedVersion = candidates.asSequence()
            .mapNotNull { it.resolvedVersion() }
            .distinct()
            .maxByOrNull { ComparableVersion(it) }
            ?: error("All conflicting candidates have no resolved version")

        val candidatesWithResolvedVersion = candidates.filter { it.resolvedVersion() == resolvedVersion }

        candidates.asSequence().forEach {
            when(it) {
                // todo (AB) don't align strictly constraint
                is MavenDependencyNode -> {
                    it.dependency = it.context.createOrReuseDependency(it.group, it.module, resolvedVersion)
                    it.overriddenBy = if (it.resolvedVersion() != resolvedVersion) candidatesWithResolvedVersion else emptyList()
                }
                is MavenDependencyConstraintNode -> {
                    it.dependencyConstraint = it.context.createOrReuseDependencyConstraint(it.group, it.module, Version(requires = resolvedVersion))
                    it.overriddenBy = if (it.resolvedVersion() != resolvedVersion) candidatesWithResolvedVersion else emptyList()
                }
            }
        }
        return true
    }
}

fun DependencyNode.resolvedVersion() =
    when(this) {
        is MavenDependencyNode -> dependency.version
        is MavenDependencyConstraintNode -> dependencyConstraint.version.resolve()
        else -> null
    }
