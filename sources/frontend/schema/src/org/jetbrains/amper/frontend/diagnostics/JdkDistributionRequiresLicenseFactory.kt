/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.jdkSettings
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.schema.JvmDistribution
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter

object JdkDistributionRequiresLicenseFactory : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId
        get() = "jdk.distribution.requires.license"

    override fun analyze(module: AmperModule, problemReporter: ProblemReporter) {
        val jdkSettings = module.jdkSettings
        val acceptableDistributions = jdkSettings.distributions ?: return
        val acknowledgedLicenses = jdkSettings.acknowledgedLicenses.toSet()
        val missingLicenses = acceptableDistributions.filter { it.value.requiresLicense && it.value !in acknowledgedLicenses }
        missingLicenses.forEach { missingLicense ->
            problemReporter.reportMessage(JdkDistributionRequiresLicense(missingLicense))
        }
    }
}

class JdkDistributionRequiresLicense(
    val distribution: TraceableEnum<JvmDistribution>,
) : PsiBuildProblem(Level.Error, BuildProblemType.InconsistentConfiguration) {
    override val buildProblemId = JdkDistributionRequiresLicenseFactory.diagnosticId
    override val message = SchemaBundle.message(buildProblemId, distribution.value.schemaValue)
    override val element: PsiElement
        get() = distribution.trace.extractPsiElement()
}
