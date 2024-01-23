/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.compose

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.schema.commonSettings
import org.jetbrains.amper.gradle.base.AmperNamingConventions
import org.jetbrains.amper.gradle.base.BindingPluginPart
import org.jetbrains.amper.gradle.base.PluginPartCtx
import org.jetbrains.amper.gradle.kmpp.KMPEAware
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.kotlinSourceSet
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class ComposePluginPart(ctx: PluginPartCtx) : KMPEAware, AmperNamingConventions, BindingPluginPart by ctx {
    override val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    override val needToApply by lazy {
        module.leafFragments.any { it.settings.compose.enabled }
    }

    override fun applyBeforeEvaluate() {
        project.plugins.apply("org.jetbrains.compose")
        val composeVersion = chooseComposeVersion(model)!!
        val commonFragment = module.fragments.find { it.fragmentDependencies.isEmpty() }
        commonFragment?.kotlinSourceSet?.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
        }
    }
}

/**
 * Try to find single compose version within model.
 */
fun chooseComposeVersion(
    model: Model,
    problemReporterContext: ProblemReporterContext? = null,
): String? {
    val knownComposeVersions = model.modules
        .map { it.origin.commonSettings.compose }
        .filter { it.enabled }
        .mapNotNull { it.version }
        .sorted()

    return if (knownComposeVersions.size > 1) {
        problemReporterContext
            ?.problemReporter
            ?.reportError("Multiple compose versions declared. Can't apply them all.")
        knownComposeVersions.first()
    } else if (knownComposeVersions.isEmpty()) null
    else knownComposeVersions.first()
}
