/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import kotlinx.serialization.Serializable
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.schema.JavaVersion
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.KotlinVersion
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
import org.jetbrains.amper.tasks.CompileTask

@Serializable
internal data class CompilationUserSettings(
    val kotlin: KotlinUserSettings,
    val jvmRelease: JavaVersion?,
)

@Serializable
internal data class KotlinUserSettings(
    val languageVersion: KotlinVersion,
    val apiVersion: KotlinVersion,
    val allWarningsAsErrors: Boolean,
    val suppressWarnings: Boolean,
    val debug: Boolean,
    val verbose: Boolean,
    val progressiveMode: Boolean,
    val languageFeatures: List<String>,
    val optIns: List<String>,
    val freeCompilerArgs: List<String>,
    val serializationEnabled: Boolean,
    val composeEnabled: Boolean,
)

context(CompileTask)
internal fun List<Fragment>.mergedCompilationSettings(): CompilationUserSettings = CompilationUserSettings(
    kotlin = mergedKotlinSettings(),
    jvmRelease = unanimousOptionalSetting("jvm.release") { it.jvm.release },
)

// TODO Consider for which Kotlin settings we should enforce consistency between fragments.
//  Currently we compile all related fragments together (we don't do klib for common separately), so we have to use
//  consistent compiler arguments. This is why we forbid configurations where some fragments diverge.
context(CompileTask)
internal fun List<Fragment>.mergedKotlinSettings(): KotlinUserSettings = KotlinUserSettings(
    languageVersion = unanimousKotlinSetting("languageVersion") { it.languageVersion },
    apiVersion = unanimousKotlinSetting("apiVersion") { it.apiVersion },
    allWarningsAsErrors = unanimousKotlinSetting("allWarningsAsErrors") { it.allWarningsAsErrors },
    suppressWarnings = unanimousKotlinSetting("suppressWarnings") { it.suppressWarnings },
    debug = unanimousKotlinSetting("debug") { it.debug },
    verbose = unanimousKotlinSetting("verbose") { it.verbose },
    progressiveMode = unanimousKotlinSetting("progressiveMode") { it.progressiveMode },
    languageFeatures = unanimousOptionalKotlinSetting("languageFeatures") { it.languageFeatures } ?: emptyList(),
    optIns = unanimousKotlinSetting("optIns") { it.optIns ?: emptyList() },
    freeCompilerArgs = unanimousOptionalKotlinSetting("freeCompilerArgs") { it.freeCompilerArgs } ?: emptyList(),
    serializationEnabled = !unanimousOptionalKotlinSetting("serialization.format") { it.serialization?.format }.isNullOrBlank(),
    composeEnabled = unanimousOptionalSetting("compose.enabled") { it.compose.enabled } ?: false,
)

context(CompileTask)
private fun <T : Any> List<Fragment>.unanimousOptionalKotlinSetting(settingFqn: String, selector: (KotlinSettings) -> T?): T? =
    unanimousOptionalSetting("kotlin.$settingFqn") { selector(it.kotlin) }

context(CompileTask)
private fun <T : Any> List<Fragment>.unanimousKotlinSetting(settingFqn: String, selector: (KotlinSettings) -> T): T =
    unanimousSetting("kotlin.$settingFqn") { selector(it.kotlin) }

context(CompileTask)
private fun <T : Any> List<Fragment>.unanimousSetting(settingFqn: String, selector: (Settings) -> T): T =
    unanimousOptionalSetting(settingFqn, selector)
        ?: error("Module '${module.userReadableName}' has no fragments, cannot merge setting '$settingFqn'")

context(CompileTask)
private fun <T> List<Fragment>.unanimousOptionalSetting(settingFqn: String, selector: (Settings) -> T): T? {
    val distinctValues = mapNotNull { selector(it.settings) }.distinct()
    if (distinctValues.size > 1) {
        error("The fragments ${userReadableList()} of module '${module.userReadableName}' are compiled " +
                "together but provide several different values for 'settings.$settingFqn': $distinctValues")
    }
    return distinctValues.singleOrNull()
}
