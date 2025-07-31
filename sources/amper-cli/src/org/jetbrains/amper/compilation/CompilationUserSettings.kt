/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import kotlinx.serialization.Serializable
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.schema.JavaVersion
import org.jetbrains.amper.frontend.schema.KotlinVersion

@Serializable // makes it convenient to include in the input properties of the incremental cache state
internal data class CompilationUserSettings(
    val kotlin: KotlinUserSettings,
    val jvmRelease: JavaVersion?,
    val java: JavaUserSettings,
)

@Serializable
internal data class KotlinUserSettings(
    val languageVersion: KotlinVersion,
    val apiVersion: KotlinVersion,
    val allWarningsAsErrors: Boolean,
    val suppressWarnings: Boolean,
    val debug: Boolean?,
    val optimization: Boolean?,
    val verbose: Boolean,
    val progressiveMode: Boolean,
    val languageFeatures: List<String>,
    val optIns: List<String>,
    val storeJavaParameterNames: Boolean,
    val freeCompilerArgs: List<String>,
    val compilerPlugins: List<SCompilerPluginConfig>,
)

@Serializable
internal data class JavaUserSettings(
    val parameters: Boolean = false,
    val freeCompilerArgs: List<String>,
)

internal fun Fragment.serializableCompilationSettings(): CompilationUserSettings = CompilationUserSettings(
    kotlin = serializableKotlinSettings(),
    jvmRelease = settings.jvm.release,
    java = serializableJavaSettings(),
)

internal fun Fragment.serializableKotlinSettings(): KotlinUserSettings = KotlinUserSettings(
    languageVersion = settings.kotlin.languageVersion,
    apiVersion = settings.kotlin.apiVersion,
    allWarningsAsErrors = settings.kotlin.allWarningsAsErrors,
    suppressWarnings = settings.kotlin.suppressWarnings,
    debug = settings.kotlin.debug,
    optimization = settings.kotlin.optimization, // only valid for native anyway
    verbose = settings.kotlin.verbose,
    progressiveMode = settings.kotlin.progressiveMode,
    languageFeatures = settings.kotlin.languageFeatures?.values().orEmpty(),
    optIns = settings.kotlin.optIns.orEmpty().values(),
    storeJavaParameterNames = settings.jvm.storeParameterNames, // only valid for JVM anyway
    // We cannot know whether the free compiler args must be aligned, so let's not fail hastily.
    freeCompilerArgs = settings.kotlin.freeCompilerArgs?.values().orEmpty(),
    compilerPlugins = compilerPluginConfigs(),
)

private fun Fragment.serializableJavaSettings(): JavaUserSettings = JavaUserSettings(
    parameters = settings.jvm.storeParameterNames,
    freeCompilerArgs = settings.java.freeCompilerArgs.values(),
)

/**
 * Returns the single leaf fragment of this list, or throws an exception if there isn't exactly one leaf fragments.
 */
internal fun List<Fragment>.singleLeafFragment(): Fragment = singleOrNull { it is LeafFragment }
    ?: error("Expected one single leaf fragment, got: ${map { it.name }}")

private fun List<TraceableString>.values(): List<String> = map { it.value }
