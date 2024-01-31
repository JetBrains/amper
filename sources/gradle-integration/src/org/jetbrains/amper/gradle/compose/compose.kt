/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.compose

import org.jetbrains.amper.frontend.aomBuilder.chooseComposeVersion
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