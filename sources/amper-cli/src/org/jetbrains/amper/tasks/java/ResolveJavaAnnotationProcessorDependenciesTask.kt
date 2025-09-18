/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.java

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenDependency
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.tasks.AbstractResolveJvmExternalDependenciesTask

internal class ResolveJavaAnnotationProcessorDependenciesTask(
    override val taskName: TaskName,
    module: AmperModule,
    private val fragments: List<Fragment>,
    userCacheRoot: AmperUserCacheRoot,
    incrementalCache: IncrementalCache,
) : AbstractResolveJvmExternalDependenciesTask(
    module,
    userCacheRoot,
    incrementalCache,
    "Java annotation processors for ",
) {
    
    override fun getMavenCoordinatesToResolve() = fragments.flatMap { it.settings.java.annotationProcessing.processors }
        // catalog references have been handled in the frontend, so we don't need to resolve them here
        .filterIsInstance<UnscopedExternalMavenDependency>()
        .map { it.coordinates }
}
