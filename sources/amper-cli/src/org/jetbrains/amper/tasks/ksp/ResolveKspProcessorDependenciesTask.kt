/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ksp

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.schema.UnscopedDependency
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.tasks.AbstractResolveJvmExternalDependenciesTask

internal class ResolveKspProcessorDependenciesTask(
    override val taskName: TaskName,
    module: AmperModule,
    private val fragments: List<Fragment>,
    userCacheRoot: AmperUserCacheRoot,
    incrementalCache: IncrementalCache,
) : AbstractResolveJvmExternalDependenciesTask(module, userCacheRoot, incrementalCache, "KSP processors for ") {

    override fun getMavenCoordinatesToResolve(): List<UnscopedDependency> =
        fragments.flatMap { it.settings.kotlin.ksp.processors }
}
