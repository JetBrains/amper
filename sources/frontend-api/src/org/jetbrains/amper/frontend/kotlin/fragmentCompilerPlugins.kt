/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.kotlin

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.schema.AllOpenPreset
import org.jetbrains.amper.frontend.schema.NoArgPreset
import org.jetbrains.amper.frontend.schema.Settings

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
}

// for now the schema and compiler value are aligned
private val AllOpenPreset.compilerArgValue: String get() = schemaValue

// for now the schema and compiler value are aligned
private val NoArgPreset.compilerArgValue: String get() = schemaValue
