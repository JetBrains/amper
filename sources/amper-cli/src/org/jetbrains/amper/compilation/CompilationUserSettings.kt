/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import kotlinx.serialization.Serializable
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.schema.JavaVersion
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.KotlinVersion
import org.jetbrains.amper.settings.unanimousOptionalSetting
import org.jetbrains.amper.settings.unanimousSetting

@Serializable // makes it convenient to include in the input properties of the incremental cache state
internal data class CompilationUserSettings(
    val kotlin: KotlinUserSettings,
    val jvmRelease: JavaVersion?,
    val java: JavaUserSettings = JavaUserSettings(),
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
    val storeJavaParameterNames: Boolean,
    val freeCompilerArgs: List<String>,
    val compilerPlugins: List<SCompilerPluginConfig>,
)

@Serializable
internal data class JavaUserSettings(val parameters: Boolean = false)

internal fun List<Fragment>.mergedCompilationSettings(): CompilationUserSettings = CompilationUserSettings(
    kotlin = mergedKotlinSettings(),
    jvmRelease = unanimousOptionalSetting("jvm.release") { it.jvm.release },
    java = mergedJavaSettings(),
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
    storeJavaParameterNames = unanimousSetting("jvm.storeParameterNames") { it.jvm.storeParameterNames },
    freeCompilerArgs = unanimousOptionalKotlinSetting("freeCompilerArgs") { it.freeCompilerArgs?.map { it.value } }.orEmpty(),
    compilerPlugins = mergeCompilerPluginConfigs(),
)

private fun List<Fragment>.mergedJavaSettings(): JavaUserSettings = JavaUserSettings(
    parameters = unanimousSetting("jvm.storeParameterNames") { it.jvm.storeParameterNames },
)

private fun <T : Any> List<Fragment>.unanimousOptionalKotlinSetting(settingFqn: String, selector: (KotlinSettings) -> T?): T? =
    unanimousOptionalSetting("kotlin.$settingFqn") { selector(it.kotlin) }

private fun <T : Any> List<Fragment>.unanimousKotlinSetting(settingFqn: String, selector: (KotlinSettings) -> T): T =
    unanimousSetting("kotlin.$settingFqn") { selector(it.kotlin) }
