/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import kotlinx.serialization.Serializable
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.schema.KotlinVersion

@Serializable // makes it convenient to include in the input properties of the incremental cache state
internal data class CompilationUserSettings(
    val kotlin: KotlinUserSettings,
    val jvmRelease: JavaVersion?,
    val java: JavaUserSettings,
)

@Serializable
internal data class KotlinUserSettings(
    val compilerVersion: String,
    val languageVersion: KotlinVersion?,
    val apiVersion: KotlinVersion?,
    val allWarningsAsErrors: Boolean,
    val suppressWarnings: Boolean,
    val debug: Boolean?,
    val optimization: Boolean?,
    val verbose: Boolean,
    val progressiveMode: Boolean,
    val linkerOptions: List<String>,
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
    val annotationProcessorOptions: Map<String, String>,
)

@Serializable
@JvmInline
internal value class JavaVersion(
    /**
     * The integer representation of this version for the `--release` option of the Java compiler, and the
     * `-Xjdk-release` option of the Kotlin compiler.
     *
     * Notes:
     *  * The Java compiler only supports versions from 6 and above for the `--release` option.
     *  * Despite the documentation, the Kotlin compiler supports "8" as an alias for "1.8" in `-Xjdk-release`, but the
     *    `-jvm-target` option requires "1.8".
     */
    val releaseNumber: Int
) {
    /**
     * The legacy notation of this version, which uses the "1." prefix for every version before 9.
     *
     * Examples: 1.6, 1.7, 1.8, 9, 10, 11, 17, 21
     */
    val legacyNotation: String
        get() = if (releaseNumber <= 8) "1.$releaseNumber" else releaseNumber.toString()
}

internal fun Fragment.serializableCompilationSettings(): CompilationUserSettings = CompilationUserSettings(
    kotlin = serializableKotlinSettings(),
    jvmRelease = settings.jvm.release?.let { JavaVersion(it) },
    java = serializableJavaSettings(),
)

internal fun Fragment.serializableKotlinSettings(): KotlinUserSettings = KotlinUserSettings(
    compilerVersion = settings.kotlin.version,
    languageVersion = settings.kotlin.languageVersion,
    apiVersion = settings.kotlin.apiVersion,
    allWarningsAsErrors = settings.kotlin.allWarningsAsErrors,
    suppressWarnings = settings.kotlin.suppressWarnings,
    debug = settings.kotlin.debug,
    optimization = settings.kotlin.optimization, // only valid for native anyway
    verbose = settings.kotlin.verbose,
    progressiveMode = settings.kotlin.progressiveMode,
    linkerOptions = settings.kotlin.linkerOptions?.values().orEmpty(),
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
    annotationProcessorOptions = settings.java.annotationProcessing.processorOptions.mapValues {
        (_, value) -> value.value
    }
)

/**
 * Returns the single leaf fragment of this list, or throws an exception if there isn't exactly one leaf fragments.
 */
internal fun List<Fragment>.singleLeafFragment(): Fragment = singleOrNull { it is LeafFragment }
    ?: error("Expected one single leaf fragment, got: ${map { it.name }}")

private fun List<TraceableString>.values(): List<String> = map { it.value }
