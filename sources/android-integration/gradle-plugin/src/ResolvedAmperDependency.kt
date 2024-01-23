/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.dependencies.SelfResolvingDependencyInternal
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import java.io.File

class ResolvedAmperDependency(private val project: Project, private val flatDependency: ResolvedDependency) : FileCollectionDependency, SelfResolvingDependencyInternal {
    private var _reason: String? = null
    override fun getGroup(): String = flatDependency.group

    override fun getName(): String = flatDependency.artifact

    override fun getVersion(): String = flatDependency.version

    override fun contentEquals(dependency: Dependency): Boolean = dependency.group == flatDependency.group && dependency.name == flatDependency.artifact && dependency.version == flatDependency.version

    override fun copy(): Dependency = ResolvedAmperDependency(project, flatDependency)

    override fun getReason(): String? = _reason

    override fun because(reason: String?) {
        this._reason = reason
    }

    override fun getBuildDependencies(): TaskDependency = DefaultTaskDependencyFactory.withNoAssociatedProject().visitingDependencies {
    }

    override fun resolve(): MutableSet<File> = mutableSetOf(flatDependency.path.toFile())

    override fun resolve(transitive: Boolean): MutableSet<File> = mutableSetOf(flatDependency.path.toFile())

    override fun getTargetComponentId(): ComponentIdentifier = OpaqueComponentArtifactIdentifier(flatDependency.path.toFile())

    override fun getFiles(): FileCollection = project.files(flatDependency.path.toAbsolutePath().toString())
}