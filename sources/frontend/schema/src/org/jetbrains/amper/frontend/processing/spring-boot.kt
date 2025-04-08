/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.schema.AllOpenPreset
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.NoArgPreset

fun Module.configureSpringBootKotlinCompilerPlugins() = apply {
    // temporary ugly hack until we have dependent values on external nodes
    settings.values.forEach { fragmentSettings ->
        if (fragmentSettings.springBoot.enabled) {
            if (fragmentSettings.kotlin.allOpen.trace == null) { // the user explicitly hasn't touched the setting
                fragmentSettings.kotlin.allOpen.enabled = true
                fragmentSettings.kotlin.allOpen.presets = listOf(AllOpenPreset.Spring)
            }

            if (fragmentSettings.kotlin.noArg.trace == null) { // the user explicitly hasn't touched the setting
                fragmentSettings.kotlin.noArg.enabled = true
                fragmentSettings.kotlin.noArg.presets = listOf(NoArgPreset.Jpa)
            }

            // as default is empty at this stage, it means the user hasn't touched it
            if (fragmentSettings.kotlin.freeCompilerArgs?.isEmpty() == true) {
                fragmentSettings.kotlin.freeCompilerArgs = listOf(TraceableString("-Xjsr305=strict"))
            }

            fragmentSettings.jvm.parameters = true
        }
    }
}
