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

internal fun List<Fragment>.mergedCompilationSettings(): CompilationUserSettings = CompilationUserSettings(
    kotlin = mergedKotlinSettings(),
    jvmRelease = unanimousOptionalSetting("jvm.release") { it.jvm.release },
)

// TODO Consider for which Kotlin settings we should enforce consistency between fragments.
//  Currently we compile all related fragments together (we don't do klib for common separately), so we have to use
//  consistent compiler arguments. This is why we forbid configurations where some fragments diverge.
internal fun List<Fragment>.mergedKotlinSettings(): KotlinUserSettings = KotlinUserSettings(
    languageVersion = unanimousKotlinSetting("languageVersion") { it.languageVersion },
    apiVersion = unanimousKotlinSetting("apiVersion") { it.apiVersion },
    allWarningsAsErrors = unanimousKotlinSetting("allWarningsAsErrors") { it.allWarningsAsErrors },
    suppressWarnings = unanimousKotlinSetting("suppressWarnings") { it.suppressWarnings },
    debug = unanimousKotlinSetting("debug") { it.debug },
    verbose = unanimousKotlinSetting("verbose") { it.verbose },
    progressiveMode = unanimousKotlinSetting("progressiveMode") { it.progressiveMode },
    languageFeatures = unanimousOptionalKotlinSetting("languageFeatures") { it.languageFeatures?.map { it.value } }.orEmpty(),
    optIns = unanimousKotlinSetting("optIns") { it.optIns.orEmpty().map { it.value } },
    freeCompilerArgs = unanimousOptionalKotlinSetting("freeCompilerArgs") { it.freeCompilerArgs?.map { it.value } }.orEmpty(),
    serializationEnabled = !unanimousOptionalKotlinSetting("serialization.format") { it.serialization?.format }.isNullOrBlank(),
    composeEnabled = unanimousOptionalSetting("compose.enabled") { it.compose.enabled } ?: false,
)

private fun <T : Any> List<Fragment>.unanimousOptionalKotlinSetting(settingFqn: String, selector: (KotlinSettings) -> T?): T? =
    unanimousOptionalSetting("kotlin.$settingFqn") { selector(it.kotlin) }

private fun <T : Any> List<Fragment>.unanimousKotlinSetting(settingFqn: String, selector: (KotlinSettings) -> T): T =
    unanimousSetting("kotlin.$settingFqn") { selector(it.kotlin) }

/**
 * Gets a single common value for a particular setting among these fragments, and throws an exception if 2 fragments
 * have a different value for it, or if there are no fragments at all.
 *
 * The setting is accessed using the provided [selector]. [settingFqn] is only used for error reporting.
 */
private fun <T : Any> List<Fragment>.unanimousSetting(settingFqn: String, selector: (Settings) -> T): T =
    unanimousOptionalSetting(settingFqn, selector)
        ?: error("No fragments provided, cannot merge setting '$settingFqn'")

/**
 * Gets a single common value for a particular setting among these fragments, and throws an exception if 2 fragments
 * have a different value for it. If a fragment doesn't specify a value for the setting (null), it doesn't count as
 * having a different value and doesn't throw an exception.
 *
 * The setting is accessed using the provided [selector]. [settingFqn] is only used for error reporting.
 */
private fun <T> List<Fragment>.unanimousOptionalSetting(settingFqn: String, selector: (Settings) -> T): T? {
    val distinctValues = mapNotNull { selector(it.settings) }.distinct()
    if (distinctValues.size > 1) {
        error("The fragments ${userReadableList()} of module '${first().module.userReadableName}' are compiled " +
                "together but provide several different values for 'settings.$settingFqn': $distinctValues")
    }
    return distinctValues.singleOrNull()
}
