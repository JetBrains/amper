/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.kotlin.CompilerPluginConfig
import org.jetbrains.amper.frontend.kotlin.ParcelizeCompilerPluginConfig
import org.jetbrains.amper.frontend.kotlin.compilerPluginsConfigurations
import java.nio.file.Path

/**
 * The configuration for a Kotlin compiler plugin.
 * This class is serializable so it can be used in cached incremental state.
 */
@Serializable
internal data class SCompilerPluginConfig(
    /**
     * The plugin ID used to associate options with the corresponding plugin when calling the compiler.
     * It is exposed by each plugin's implementation in their `CommandLineProcessor.pluginId` property, or in
     * `KotlinCompilerPluginSupportPlugin.getCompilerPluginId`.
     */
    val id: String,
    /**
     * Options configured for this compiler plugin.
     */
    val options: List<Option>,
    /**
     * The maven coordinates to download the plugin from.
     */
    val coordinates: MavenCoordinates,
) {
    @Serializable
    data class Option(val name: String, val value: String)

    @Serializable
    data class MavenCoordinates(
        val groupId: String,
        val artifactId: String,
        val version: String,
    )
}

/**
 * Gets the configurations of the compiler plugins to use to compile these fragments in a single compilation.
 *
 * This function throws an exception if multiple fragments have contradicting configs of the same plugin.
 * The config of a given plugin is expected to be identical for all fragments because they are compiled together.
 */
internal fun List<Fragment>.mergeCompilerPluginConfigs(): List<SCompilerPluginConfig> {
    val isAndroidCompilation = all { it.platforms.contains(Platform.ANDROID) }
    // we disable Parcelize on any code that doesn't compile to Android, because it fails on missing Parcelable
    val pluginConfigs = flatMap { it.settings.compilerPluginsConfigurations() }
        .filter { isAndroidCompilation || it !is ParcelizeCompilerPluginConfig }

    return pluginConfigs.groupBy { it.id }.values
        .map { configsOfSinglePlugin -> ensureSingleUnanimousConfig(configsOfSinglePlugin) }
        .map { it.toSerializableCompilerPluginConfig() }
}

private fun ensureSingleUnanimousConfig(configsOfSamePlugin: List<CompilerPluginConfig>): CompilerPluginConfig =
    configsOfSamePlugin.distinctBy { it.options }.singleOrNull()
        ?: userReadableError("The compiler plugin '${configsOfSamePlugin.first().id}' is configured with different " +
                "settings for some fragments that are compiled together:\n" +
                configsOfSamePlugin.joinToString("\n") { it.optionsSummary() })

private fun CompilerPluginConfig.optionsSummary(): String = options.joinToString { "${it.name}=${it.value}" }

private fun CompilerPluginConfig.toSerializableCompilerPluginConfig(): SCompilerPluginConfig = SCompilerPluginConfig(
    id = id,
    options = options.map { SCompilerPluginConfig.Option(it.name, it.value) },
    coordinates = SCompilerPluginConfig.MavenCoordinates(
        groupId = mavenCoordinates.groupId,
        artifactId = mavenCoordinates.artifactId,
        version = mavenCoordinates.version
    ),
)

/**
 * A Kotlin compiler plugin configuration ready to be passed to the compiler.
 * It has a resolved [classpath] of the jars of the plugin (and its dependencies).
 */
internal data class ResolvedCompilerPlugin(
    /**
     * The plugin ID used to associate arguments with the corresponding plugin.
     * It is exposed by each plugin's implementation in their `CommandLineProcessor.pluginId` property.
     */
    val id: String,
    /**
     * The classpath of the plugin to pass to the compiler (including the plugin jar and its dependencies).
     */
    val classpath: List<Path>,
    /**
     * Options configured for this compiler plugin.
     */
    val options: List<SCompilerPluginConfig.Option> = emptyList(), // not a map because options can be repeated
)

/**
 * Downloads the jars of each compiler plugin and their dependencies, and returns ready-to-use
 * [ResolvedCompilerPlugin]s.
 */
internal suspend fun KotlinArtifactsDownloader.downloadCompilerPlugins(
    plugins: List<SCompilerPluginConfig>,
): List<ResolvedCompilerPlugin> = coroutineScope {
    plugins.map {
        async {
            ResolvedCompilerPlugin(
                id = it.id,
                classpath = downloadKotlinCompilerPlugin(it),
                options = it.options
            )
        }
    }.awaitAll()
}
