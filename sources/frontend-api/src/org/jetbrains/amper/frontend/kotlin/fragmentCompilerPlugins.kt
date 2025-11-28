/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.kotlin

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.MavenCoordinates
import org.jetbrains.amper.frontend.api.asTraceable
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.frontend.kotlin.CompilerPluginConfig.*
import org.jetbrains.amper.frontend.schema.AllOpenPreset
import org.jetbrains.amper.frontend.schema.NoArgPreset
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schema.UnscopedCatalogDependency
import org.jetbrains.amper.frontend.schema.UnscopedExternalDependency
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenDependency
import org.jetbrains.amper.frontend.schema.toMavenCoordinates

/**
 * Returns the Kotlin compiler plugins configurations defined by these [Settings].
 */
@UsedInIdePlugin
fun Settings.compilerPluginsConfigurations(): List<CompilerPluginConfig> = buildList {
    if (kotlin.serialization.enabled) {
        add(SerializationCompilerPluginConfig(kotlinVersion = kotlin.version))
    }

    if (compose.enabled) {
        add(ComposeCompilerPluginConfig(kotlinVersion = kotlin.version))
    }

    if (android.parcelize.enabled) {
        add(
            ParcelizeCompilerPluginConfig(
                kotlinVersion = kotlin.version,
                additionalAnnotations = android.parcelize.additionalAnnotations.map { it.value },
            )
        )
    }

    if (kotlin.noArg.enabled) {
        add(
            NoArgCompilerPluginConfig(
                kotlinVersion = kotlin.version,
                annotations = kotlin.noArg.annotations?.map { it.value } ?: emptyList(),
                presets = kotlin.noArg.presets?.map { it.compilerArgValue } ?: emptyList(),
                invokeInitializers = kotlin.noArg.invokeInitializers,
            )
        )
    }

    if (kotlin.allOpen.enabled) {
        add(
            AllOpenCompilerPluginConfig(
                kotlinVersion = kotlin.version,
                annotations = kotlin.allOpen.annotations?.map { it.value } ?: emptyList(),
                presets = kotlin.allOpen.presets?.map { it.compilerArgValue } ?: emptyList(),
            )
        )
    }

    if (kotlin.jsPlainObjects.enabled) {
        add(
            JsPlainObjectsCompilerPluginConfig(kotlinVersion = kotlin.version)
        )
    }

    if (kotlin.powerAssert.enabled) {
        add(
            PowerAssertCompilerPluginConfig(
                kotlinVersion = kotlin.version,
                functions = kotlin.powerAssert.functions.map { it.value },
            )
        )
    }

    if (kotlin.rpc.enabled) {
        addAll(
            kotlinxRpcCompilerPlugins(
                kotlinVersion = kotlin.version,
                kotlinxRpcVersion = kotlin.rpc.version,
                annotationTypeSafetyEnabled = kotlin.rpc.annotationTypeSafetyEnabled,
            )
        )
    }

    if (lombok.enabled) {
        add(LombokCompilerPluginConfig(kotlinVersion = kotlin.version))
    }

    kotlin.compilerPlugins.forEach { plugin ->
        add(
            ThirdPartyCompilerPluginConfig(
                id = plugin.id,
                options = plugin.options.map { Option(it.key.value, it.value.value) },
                mavenCoordinates = plugin.dependency.toMavenCoordinates(),
            )
        )
    }
}

private fun UnscopedExternalDependency.toMavenCoordinates(): CompilerPluginConfig.MavenCoordinates = when (this) {
    is UnscopedExternalMavenDependency -> coordinates
        .asTraceable(::coordinates.schemaDelegate.trace)
        .toMavenCoordinates()
        .toPluginMavenCoordinates()
    is UnscopedCatalogDependency -> error("Catalog dependencies should be substituted earlier")
}

private fun MavenCoordinates.toPluginMavenCoordinates(): CompilerPluginConfig.MavenCoordinates = MavenCoordinates(
    groupId = groupId,
    artifactId = artifactId,
    version = version ?: error("'version' is required for compiler plugin dependencies"),
)

// for now the schema and compiler value are aligned
private val AllOpenPreset.compilerArgValue: String get() = schemaValue

// for now the schema and compiler value are aligned
private val NoArgPreset.compilerArgValue: String get() = schemaValue
