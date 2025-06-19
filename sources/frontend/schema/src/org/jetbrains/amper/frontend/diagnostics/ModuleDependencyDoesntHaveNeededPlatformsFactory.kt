/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.aomBuilder.NotResolvedModule
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.annotations.Nls

object ModuleDependencyDoesntHaveNeededPlatformsFactory : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId = ModuleDependencyDoesntHaveNeededPlatforms.ID

    context(ProblemReporterContext)
    override fun AmperModule.analyze() {
        val reportedPlaces = mutableSetOf<Trace?>()
        for (fragment in fragments) {
            val fragmentPlatforms = fragment.platforms
            val localDependencies = fragment.externalDependencies.filterIsInstance<LocalModuleDependency>()
            for (localDependency in localDependencies) {
                // We'll ignore not resolved modules as another error will be reported on them instead.
                if (localDependency.module is NotResolvedModule) continue

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
) : PsiBuildProblem(Level.Error) {
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
