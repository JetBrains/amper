package org.jetbrains.amper.dependency.resolution

import org.apache.maven.artifact.versioning.ComparableVersion

interface ConflictResolutionStrategy {
    fun isApplicableFor(candidates: LinkedHashSet<DependencyNode>): Boolean
    fun seesConflictsIn(candidates: LinkedHashSet<DependencyNode>): Boolean
    fun resolveConflictsIn(candidates: LinkedHashSet<DependencyNode>)
}

class HighestVersionStrategy : ConflictResolutionStrategy {

    override fun isApplicableFor(candidates: LinkedHashSet<DependencyNode>): Boolean =
        candidates.all { it is MavenDependencyNode }

    override fun seesConflictsIn(candidates: LinkedHashSet<DependencyNode>): Boolean =
        candidates.map { it as MavenDependencyNode }
            .map { it.dependency }
            .distinctBy { ComparableVersion(it.version) }
            .size > 1

    override fun resolveConflictsIn(candidates: LinkedHashSet<DependencyNode>) {
        val mavenDependencyNodes = candidates.map { it as MavenDependencyNode }
        val dependency = mavenDependencyNodes.map { it.dependency }.maxWith(
            compareBy<MavenDependency> { ComparableVersion(it.version) }.thenBy { it.state }
        )
        mavenDependencyNodes.forEach { it.dependency = dependency }
    }
}
