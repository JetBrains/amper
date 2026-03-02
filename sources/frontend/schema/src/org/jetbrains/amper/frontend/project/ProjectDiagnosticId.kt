/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import org.jetbrains.amper.problems.reporting.DiagnosticId

/**
 * Diagnostics reported about the project file.
 */
enum class ProjectDiagnosticId : DiagnosticId {
    DoubleStarNotSupportedInGlobs,
    GlobMatchesNothing,
    InvalidGlobPattern,
    ProjectHasNoModules,
    ModuleDirectoryIsNotUnderRoot,
    ModuleDirectoryHasNoModuleFile,
    ModuleReferenceIsNotDirectory,
    ModuleRootIsIncludedByDefault,
    PluginDependencyNotFound,
    PluginDependencyNotIncludedAsModule,
}