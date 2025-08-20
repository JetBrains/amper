/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.annotations.Nls

object ModuleDependencyDoesntHaveNeededPlatformsFactory : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId = ModuleDependencyDoesntHaveNeededPlatforms.ID

    override fun analyze(module: AmperModule, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<Trace?>()
        for (fragment in module.fragments) {
            val fragmentPlatforms = fragment.platforms
            val localDependencies = fragment.externalDependencies.filterIsInstance<LocalModuleDependency>()
            for (localDependency in localDependencies) {
                val localDependencyPlatforms = localDependency.module.leafPlatforms
                if (fragmentPlatforms.any { it !in localDependencyPlatforms } && reportedPlaces.add(localDependency.trace)) {
                    problemReporter.reportMessage(
                        ModuleDependencyDoesntHaveNeededPlatforms(localDependency, fragment)
                    )
                }
            }
        }
    }
}

class ModuleDependencyDoesntHaveNeededPlatforms(
    val dependency: LocalModuleDependency,
    @field:UsedInIdePlugin
    val dependingFragment: Fragment,
) : PsiBuildProblem(Level.Error, BuildProblemType.InconsistentConfiguration) {
    companion object {
        const val ID = "module.dependency.doesnt.have.needed.platforms"
    }

    override val element: PsiElement
        get() = dependency.extractPsiElement()

    override val buildProblemId: BuildProblemId = ID

    override val message: @Nls String
        get() = SchemaBundle.message(
            ID,
            dependency.module.userReadableName,
            unsupportedPlatforms.map(Platform::pretty),
        )

    val unsupportedPlatforms: Set<Platform> = dependingFragment.platforms - dependency.module.leafPlatforms
}
