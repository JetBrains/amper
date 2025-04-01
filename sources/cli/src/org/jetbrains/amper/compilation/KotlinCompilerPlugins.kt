/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.nio.file.Path

internal data class CompilerPlugin(
    /**
     * The plugin ID used to associate arguments with the corresponding plugin.
     * It is exposed by each plugin's implementation in their `CommandLineProcessor.pluginId` property.
     */
    val id: String,
    val jarPath: Path,
    val options: List<Option> = emptyList(), // not a map because options can be repeated
) {
    data class Option(val name: String, val value: String)

    companion object {
        fun serialization(jarPath: Path) = CompilerPlugin(
            id = "org.jetbrains.kotlinx.serialization",
            jarPath = jarPath,
        )

        // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/android/internal/ParcelizeSubplugin.kt
        fun parcelize(jarPath: Path, additionalAnnotations: List<String>) = CompilerPlugin(
            id = "org.jetbrains.kotlin.parcelize",
            jarPath = jarPath,
            options = additionalAnnotations.map { Option(name = "additionalAnnotation", value = it) },
        )

        fun compose(jarPath: Path) = CompilerPlugin(
            id = "androidx.compose.compiler.plugins.kotlin",
            jarPath = jarPath,
            options = listOf(
                Option(name = "featureFlag", value = "OptimizeNonSkippingGroups"),
                Option(name = "generateFunctionKeyMetaAnnotations", value = "function"),
            )
        )

        // https://kotlinlang.org/docs/no-arg-plugin.html
        fun noArg(jarPath: Path, annotations: List<String>, presets: List<String>, invokeInitializers: Boolean) = CompilerPlugin(
            id = "org.jetbrains.kotlin.noarg",
            jarPath = jarPath,
            options = buildList {
                addAll(annotations.map { Option(name = "annotation", value = it) })
                addAll(presets.map { Option(name = "preset", value = it) })
                add(Option(name = "invokeInitializers", value = invokeInitializers.toString()))
            }
        )

        // https://kotlinlang.org/docs/all-open-plugin.html
        fun allOpen(jarPath: Path, annotations: List<String>, presets: List<String>) = CompilerPlugin(
            id = "org.jetbrains.kotlin.allopen",
            jarPath = jarPath,
            options = buildList {
                addAll(annotations.map { Option(name = "annotation", value = it) })
                addAll(presets.map { Option(name = "preset", value = it) })
            }
        )
    }
}

internal suspend fun KotlinArtifactsDownloader.downloadCompilerPlugins(
    kotlinVersion: String,
    kotlinUserSettings: KotlinUserSettings,
): List<CompilerPlugin> = coroutineScope {
    buildList {
        if (kotlinUserSettings.serializationEnabled) {
            val plugin = async {
                CompilerPlugin.serialization(downloadKotlinSerializationPlugin(kotlinVersion))
            }
            add(plugin)
        }
        if (kotlinUserSettings.composeEnabled) {
            val plugin = async {
                CompilerPlugin.compose(downloadKotlinComposePlugin(kotlinVersion))
            }
            add(plugin)
        }
        if (kotlinUserSettings.parcelizeEnabled) {
            val plugin = async {
                CompilerPlugin.parcelize(
                    jarPath = downloadKotlinParcelizePlugin(kotlinVersion),
                    additionalAnnotations = kotlinUserSettings.parcelizeAdditionalAnnotations,
                )
            }
            add(plugin)
        }
        if (kotlinUserSettings.noArg.enabled) {
            val plugin = async {
                CompilerPlugin.noArg(
                    jarPath = downloadKotlinNoArgPlugin(kotlinVersion),
                    annotations = kotlinUserSettings.noArg.annotations,
                    presets = kotlinUserSettings.noArg.presets,
                    invokeInitializers = kotlinUserSettings.noArg.invokeInitializers
                )
            }
            add(plugin)
        }
        if (kotlinUserSettings.allOpen.enabled) {
            val plugin = async {
                CompilerPlugin.allOpen(
                    jarPath = downloadKotlinAllOpenPlugin(kotlinVersion),
                    annotations = kotlinUserSettings.allOpen.annotations,
                    presets = kotlinUserSettings.allOpen.presets
                )
            }
            add(plugin)
        }
    }.awaitAll()
}
