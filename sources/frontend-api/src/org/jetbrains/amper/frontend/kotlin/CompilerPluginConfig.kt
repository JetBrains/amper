/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

data class SerializationCompilerPluginConfig(val kotlinVersion: String) : CompilerPluginConfig {
    // https://github.com/JetBrains/kotlin/blob/cb4652c3452c43aa5060407c0dc26746ca01eabb/libraries/tools/kotlin-serialization/src/common/kotlin/org/jetbrains/kotlinx/serialization/gradle/SerializationSubplugin.kt#L44
    override val id = "org.jetbrains.kotlinx.serialization"
    override val options = emptyList<Option>()
    override val mavenCoordinates = CompilerPluginConfig.MavenCoordinates(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-serialization-compiler-plugin-embeddable",
        version = kotlinVersion,
    )
}

data class AllOpenCompilerPluginConfig(
    val kotlinVersion: String,
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
        version = kotlinVersion,
    )
}

data class NoArgCompilerPluginConfig(
    val kotlinVersion: String,
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
        version = kotlinVersion,
    )
}

data class JsPlainObjectsCompilerPluginConfig(
    val kotlinVersion: String,
) : CompilerPluginConfig {
    // https://github.com/JetBrains/kotlin/blob/cb4652c3452c43aa5060407c0dc26746ca01eabb/libraries/tools/js-plain-objects/src/common/kotlin/org/jetbrains/kotlinx/jso/gradle/JsPlainObjectsKotlinGradleSubplugin.kt#L43
    override val id = "org.jetbrains.kotlinx.js-plain-objects"
    override val options = emptyList<Option>()
    override val mavenCoordinates = CompilerPluginConfig.MavenCoordinates(
        groupId = KOTLIN_GROUP_ID,
        // https://github.com/JetBrains/kotlin/blob/cb4652c3452c43aa5060407c0dc26746ca01eabb/libraries/tools/js-plain-objects/src/common/kotlin/org/jetbrains/kotlinx/jso/gradle/JsPlainObjectsKotlinGradleSubplugin.kt#L25
        artifactId = "kotlinx-js-plain-objects-compiler-plugin-embeddable",
        version = kotlinVersion,
    )
}

data class PowerAssertCompilerPluginConfig(
    val kotlinVersion: String,
    val functions: List<String>,
) : CompilerPluginConfig {
    // https://github.com/JetBrains/kotlin/blob/4788eb845b46d8639afafa1674f7e81028dcbfb8/plugins/power-assert/power-assert.cli/src/org/jetbrains/kotlin/powerassert/PowerAssertCommandLineProcessor.kt#L28
    // https://github.com/JetBrains/kotlin/blob/4788eb845b46d8639afafa1674f7e81028dcbfb8/libraries/tools/kotlin-power-assert/src/common/kotlin/org/jetbrains/kotlin/powerassert/gradle/PowerAssertGradlePlugin.kt#L62
    override val id = "org.jetbrains.kotlin.powerassert"
    override val options = buildList {
        addAll(functions.map { Option(name = "function", value = it) })
    }
    override val mavenCoordinates = CompilerPluginConfig.MavenCoordinates(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-power-assert-compiler-plugin-embeddable",
        version = kotlinVersion,
    )
}

data class ParcelizeCompilerPluginConfig(
    val kotlinVersion: String,
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
        version = kotlinVersion,
    )
}

data class ComposeCompilerPluginConfig(val kotlinVersion: String) : CompilerPluginConfig {
    // https://github.com/JetBrains/kotlin/blob/cb4652c3452c43aa5060407c0dc26746ca01eabb/libraries/tools/kotlin-compose-compiler/src/common/kotlin/org/jetbrains/kotlin/compose/compiler/gradle/ComposeCompilerSubplugin.kt#L146
    override val id = "androidx.compose.compiler.plugins.kotlin"
    override val options = listOf(
        // added for hot reload
        Option(name = "generateFunctionKeyMetaAnnotations", value = "true"),
        Option(name = "sourceInformation", value = "true"),
    )
    override val mavenCoordinates = CompilerPluginConfig.MavenCoordinates(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-compose-compiler-plugin-embeddable",
        version = kotlinVersion,
    )
}

data class LombokCompilerPluginConfig(val kotlinVersion: String) : CompilerPluginConfig {
    override val id = "org.jetbrains.kotlin.lombok"
    override val options = emptyList<Option>()
    override val mavenCoordinates = CompilerPluginConfig.MavenCoordinates(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-lombok-compiler-plugin-embeddable",
        version = kotlinVersion,
    )
}

@Suppress("unused") // currently provided so the IDE can prepare 3rd-party compiler plugin support
data class ThirdPartyCompilerPluginConfig(
    override val id: String,
    override val options: List<Option>,
    override val mavenCoordinates: CompilerPluginConfig.MavenCoordinates,
) : CompilerPluginConfig
