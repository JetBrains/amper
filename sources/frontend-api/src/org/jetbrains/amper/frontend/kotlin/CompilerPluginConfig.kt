/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.kotlin

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.downloader.KOTLIN_GROUP_ID
import org.jetbrains.amper.frontend.kotlin.CompilerPluginConfig.Option

/**
 * The configuration for a Kotlin compiler plugin.
 */
@UsedInIdePlugin
sealed interface CompilerPluginConfig {
    /**
     * The plugin ID used to associate options with the corresponding plugin when calling the compiler.
     * It is exposed by each plugin's implementation in their `CommandLineProcessor.pluginId` property, or in
     * `KotlinCompilerPluginSupportPlugin.getCompilerPluginId`.
     */
    val id: String

    /**
     * Options configured for this compiler plugin.
     */
    val options: List<Option>

    /**
     * The maven coordinates to download the plugin from.
     */
    val mavenCoordinates: MavenCoordinates

    data class Option(val name: String, val value: String)

    data class MavenCoordinates(
        val groupId: String,
        val artifactId: String,
        val version: String,
    )
}

data class SerializationCompilerPluginConfig(val version: String) : CompilerPluginConfig {
    // https://github.com/JetBrains/kotlin/blob/cb4652c3452c43aa5060407c0dc26746ca01eabb/libraries/tools/kotlin-serialization/src/common/kotlin/org/jetbrains/kotlinx/serialization/gradle/SerializationSubplugin.kt#L44
    override val id = "org.jetbrains.kotlinx.serialization"
    override val options = emptyList<Option>()
    override val mavenCoordinates = CompilerPluginConfig.MavenCoordinates(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-serialization-compiler-plugin-embeddable",
        version = version,
    )
}

data class AllOpenCompilerPluginConfig(
    val version: String,
    val annotations: List<String>,
    val presets: List<String>,
) : CompilerPluginConfig {
    // https://github.com/JetBrains/kotlin/blob/cb4652c3452c43aa5060407c0dc26746ca01eabb/libraries/tools/kotlin-allopen/src/common/kotlin/org/jetbrains/kotlin/allopen/gradle/AllOpenSubplugin.kt#L71
    override val id = "org.jetbrains.kotlin.allopen"
    override val options = buildList {
        addAll(annotations.map { Option(name = "annotation", value = it) })
        addAll(presets.map { Option(name = "preset", value = it) })
    }
    override val mavenCoordinates = CompilerPluginConfig.MavenCoordinates(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-allopen-compiler-plugin-embeddable",
        version = version,
    )
}

data class NoArgCompilerPluginConfig(
    val version: String,
    val annotations: List<String>,
    val presets: List<String>,
    val invokeInitializers: Boolean,
) : CompilerPluginConfig {
    // https://github.com/JetBrains/kotlin/blob/cb4652c3452c43aa5060407c0dc26746ca01eabb/libraries/tools/kotlin-noarg/src/common/kotlin/org/jetbrains/kotlin/noarg/gradle/NoArgSubplugin.kt#L75
    override val id = "org.jetbrains.kotlin.noarg"
    override val options = buildList {
        addAll(annotations.map { Option(name = "annotation", value = it) })
        addAll(presets.map { Option(name = "preset", value = it) })
        add(Option(name = "invokeInitializers", value = invokeInitializers.toString()))
    }
    override val mavenCoordinates = CompilerPluginConfig.MavenCoordinates(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-noarg-compiler-plugin-embeddable",
        version = version,
    )
}

data class ParcelizeCompilerPluginConfig(
    val version: String,
    val additionalAnnotations: List<String>,
) : CompilerPluginConfig {
    // https://github.com/JetBrains/kotlin/blob/cb4652c3452c43aa5060407c0dc26746ca01eabb/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/android/internal/ParcelizeSubplugin.kt#L44
    override val id = "org.jetbrains.kotlin.parcelize"
    override val options = additionalAnnotations.map {
        Option(name = "additionalAnnotation", value = it)
    }
    override val mavenCoordinates = CompilerPluginConfig.MavenCoordinates(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-parcelize-compiler",
        version = version,
    )
}

data class ComposeCompilerPluginConfig(val kotlinVersion: String) : CompilerPluginConfig {
    // https://github.com/JetBrains/kotlin/blob/cb4652c3452c43aa5060407c0dc26746ca01eabb/libraries/tools/kotlin-compose-compiler/src/common/kotlin/org/jetbrains/kotlin/compose/compiler/gradle/ComposeCompilerSubplugin.kt#L146
    override val id = "androidx.compose.compiler.plugins.kotlin"
    override val options = listOf(
        // added for hot reload
        Option(name = "featureFlag", value = "OptimizeNonSkippingGroups"),
        Option(name = "generateFunctionKeyMetaAnnotations", value = "function"),
    )
    override val mavenCoordinates = run {
        val legacyComposeVersion = legacyComposeCompilerVersionFor(kotlinVersion)
        if (legacyComposeVersion != null) {
            CompilerPluginConfig.MavenCoordinates(
                groupId = "org.jetbrains.compose.compiler",
                artifactId = "compiler",
                version = legacyComposeVersion,
            )
        } else {
            CompilerPluginConfig.MavenCoordinates(
                groupId = KOTLIN_GROUP_ID,
                artifactId = "kotlin-compose-compiler-plugin-embeddable", // new artifact since 2.0.0-RC2
                version = kotlinVersion, // the new artifact uses a version matching the Kotlin version
            )
        }
    }
}

/**
 * Returns the legacy Compose (multiplatform) compiler plugin version matching the given [kotlinVersion], or null if
 * the new Compose compiler artifact with the same version as Kotlin should be used (i.e. Kotlin >= 2.0.0-RC2).
 *
 * **IMPORTANT:** The user-defined Compose version is only for runtime libraries and the Compose Gradle plugin.
 * The Compose compiler has a different versioning scheme, with a mapping to the Kotlin compiler versions.
 *
 * ### Implementation note
 *
 * This mapping should not have to be updated anymore, because the Compose compiler is now part of the Kotlin repository
 * and is released with the same version as Kotlin itself.
 *
 * The original mapping in this function came from the tables in
 * [the official documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html),
 * and the
 * [mapping in the Gradle plugin](https://github.com/JetBrains/compose-multiplatform/blob/master/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/ComposeCompilerCompatibility.kt).
 */
private fun legacyComposeCompilerVersionFor(kotlinVersion: String): String? = when (kotlinVersion) {
    "1.7.10" -> "1.3.0"
    "1.7.20" -> "1.3.2.2"
    "1.8.0" -> "1.4.0"
    "1.8.10" -> "1.4.2"
    "1.8.20" -> "1.4.5"
    "1.8.21" -> "1.4.7"
    "1.8.22" -> "1.4.8"
    "1.9.0-Beta" -> "1.4.7.1-beta"
    "1.9.0-RC" -> "1.4.8-beta"
    "1.9.0" -> "1.5.1"
    "1.9.10" -> "1.5.2"
    "1.9.20-Beta" -> "1.5.2.1-Beta2"
    "1.9.20-Beta2" -> "1.5.2.1-Beta3"
    "1.9.20-RC" -> "1.5.2.1-rc01"
    "1.9.20-RC2" -> "1.5.3-rc01"
    "1.9.20" -> "1.5.3"
    "1.9.21" -> "1.5.4"
    "1.9.22" -> "1.5.8.1"
    "1.9.23" -> "1.5.13.5"
    "1.9.24" -> "1.5.14"
    "2.0.0-Beta1" -> "1.5.4-dev1-kt2.0.0-Beta1"
    "2.0.0-Beta4" -> "1.5.9-kt-2.0.0-Beta4"
    "2.0.0-Beta5" -> "1.5.11-kt-2.0.0-Beta5"
    "2.0.0-RC1" -> "1.5.11-kt-2.0.0-RC1"
    else -> null // since 2.0.0-RC2, the Compose compiler version matches the Kotlin version (/!\ different artifact)
}
