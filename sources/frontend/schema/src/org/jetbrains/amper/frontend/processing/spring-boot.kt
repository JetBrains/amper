/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.schema.AllOpenPreset
import org.jetbrains.amper.frontend.schema.JUnitVersion
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.NoArgPreset

fun Module.configureSpringBootKotlinCompilerPlugins() = apply {
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

            fragmentSettings.junit = JUnitVersion.JUNIT5
        }
    }
}
