/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ksp

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenDependency
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.tasks.AbstractResolveJvmExternalDependenciesTask

internal class ResolveKspProcessorDependenciesTask(
    override val taskName: TaskName,
    module: AmperModule,
    private val fragments: List<Fragment>,
    userCacheRoot: AmperUserCacheRoot,
    incrementalCache: IncrementalCache,
) : AbstractResolveJvmExternalDependenciesTask(module, userCacheRoot, incrementalCache, "KSP processors for ") {
    
    override fun extractCoordinates() = fragments
        .flatMap { it.settings.kotlin.ksp.processors }
        // catalog references have been handled in the frontend, so we don't need to resolve them here
        .filterIsInstance<UnscopedExternalMavenDependency>()
        .map { it.coordinates }
}
