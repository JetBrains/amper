/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.Base
import org.jetbrains.amper.frontend.schema.ComposeSettings
import org.jetbrains.amper.frontend.schema.JavaSettings
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.SerializationSettings
import org.jetbrains.amper.frontend.schema.Settings
import java.nio.file.Path


/**
 * 1. Overall convention for function signatures is: Receiver is a base and parameter is an overwrite:
 *    ```kotlin
 *      fun T.merge(overwrite: T): T
 *    ```
 * 2. Same for lambda:
 *    ```kotlin
 *      T /* base */.(T /* overwrite */) -> T
 *    ```
 */

fun Base.merge(overwrite: Base, target: () -> Base): Base = mergeNode(overwrite, target) {
    mergeNullableCollection(Base::repositories)
    mergeNodeProperty(Base::dependencies) { this + it }
    mergeNodeProperty(Base::`test-dependencies`) { this + it }

    mergeNodeProperty(Base::settings) { mergeMap(it) { overwriteSettings -> merge(overwriteSettings) } }
    mergeNodeProperty(Base::`test-settings`) { mergeMap(it) { overwriteSettings -> merge(overwriteSettings) } }
}

fun Settings.merge(overwrite: Settings) = mergeNode(overwrite, ::Settings) {
    mergeNodeProperty(Settings::java, JavaSettings::merge)
    mergeNodeProperty(Settings::jvm, JvmSettings::merge)
    mergeNodeProperty(Settings::android, AndroidSettings::merge)
    mergeNodeProperty(Settings::kotlin, KotlinSettings::merge)
    mergeNodeProperty(Settings::compose, ComposeSettings::merge)
}

fun JavaSettings.merge(overwrite: JavaSettings) = mergeNode(overwrite, ::JavaSettings) {
    mergeScalar(JavaSettings::source)
}

fun JvmSettings.merge(overwrite: JvmSettings) = mergeNode(overwrite, ::JvmSettings) {
    mergeScalar(JvmSettings::target)
    mergeScalar(JvmSettings::mainClass)
}

fun AndroidSettings.merge(overwrite: AndroidSettings) = mergeNode(overwrite, ::AndroidSettings) {
    mergeScalar(AndroidSettings::compileSdk)
    mergeScalar(AndroidSettings::minSdk)
    mergeScalar(AndroidSettings::maxSdk)
    mergeScalar(AndroidSettings::targetSdk)
    mergeScalar(AndroidSettings::applicationId)
    mergeScalar(AndroidSettings::namespace)
}

fun KotlinSettings.merge(overwrite: KotlinSettings) = mergeNode(overwrite, ::KotlinSettings) {
    mergeScalar(KotlinSettings::languageVersion)
    mergeScalar(KotlinSettings::apiVersion)
    mergeScalar(KotlinSettings::allWarningsAsErrors)
    mergeScalar(KotlinSettings::suppressWarnings)
    mergeScalar(KotlinSettings::verbose)
    mergeScalar(KotlinSettings::debug)
    mergeScalar(KotlinSettings::progressiveMode)

    mergeNullableCollection(KotlinSettings::freeCompilerArgs)
    mergeNullableCollection(KotlinSettings::linkerOpts)
    mergeNullableCollection(KotlinSettings::languageFeatures)
    mergeNullableCollection(KotlinSettings::optIns)

    mergeNodeProperty(KotlinSettings::serialization, SerializationSettings::merge)
}

fun ComposeSettings.merge(overwrite: ComposeSettings?) = mergeNode(overwrite, ::ComposeSettings) {
    mergeScalar(ComposeSettings::enabled)
}

fun SerializationSettings.merge(overwrite: SerializationSettings) = mergeNode(overwrite, ::SerializationSettings) {
    mergeScalar(SerializationSettings::engine)
}

data class MergeCtx<T : Any>(
    val target: T,
    val overwrite: T,
    val base: T,
)

/**
 * [target] - accepted as lambda to evade non-necessary invocation.
 */
fun <T> T.mergeNode(
    overwrite: T,
    target: () -> T & Any,
    block: MergeCtx<T & Any>.() -> Unit
): T  {
    return if (overwrite != null && this != null) {
        val builtTarget = target()
        MergeCtx(builtTarget, overwrite, this).apply(block).let { builtTarget }
    }
    else overwrite ?: this
}

/**
 * Shortcut for merging nullable collection property.
 */
fun <T : Any, V> MergeCtx<T>.mergeNullableCollection(prop: T.() -> ValueBase<List<V>?>) =
    doMergeCollection(prop) { this }

/**
 * Shortcut for merging collection property.
 */
fun <T : Any, V> MergeCtx<T>.mergeCollection(prop: T.() -> ValueBase<List<V>>) =
    doMergeCollection(prop) { this }

private fun <T : Any, V, CV : List<V>?> MergeCtx<T>.doMergeCollection(
    prop: T.() -> ValueBase<CV>,
    toCV: List<V>.() -> CV,
) {
    // TODO Handle collection merge tuning here.
    val targetProp = target.prop()
    val baseValue = base.prop().withoutDefault
    val overwriteValue = overwrite.prop().withoutDefault
    val result = baseValue?.toMutableList() ?: mutableListOf()
    result.addAll(overwriteValue ?: emptyList())
    targetProp(result.toCV())
}

/**
 * Shortcut for merging scalar property.
 */
fun <T : Any, V> MergeCtx<T>.mergeScalar(prop: T.() -> ValueBase<V>) {
    val targetProp = target.prop()
    val baseValue = base.prop().withoutDefault
    val overwriteValue = overwrite.prop().withoutDefault
    targetProp(overwriteValue ?: baseValue)
}

/**
 * Shortcut for merging [ValueBase] property.
 */
fun <T : Any, V> MergeCtx<T>.mergeNodeProperty(
    prop: T.() -> ValueBase<V>,
    doMerge: (V & Any).(V & Any) -> V,
) = apply {
    val targetProp = target.prop()
    val baseValue = base.prop().withoutDefault
    val overwriteValue = overwrite.prop().withoutDefault
    when {
        baseValue != null && overwriteValue != null -> targetProp(baseValue.doMerge(overwriteValue))
        baseValue != null && overwriteValue == null -> targetProp(baseValue)
        baseValue == null && overwriteValue != null -> targetProp(overwriteValue)
        else -> Unit
    }
}

/**
 * Shortcut for merging map property.
 */
fun <K, V> Map<K, V>.mergeMap(overwrite: Map<K, V>, merge: V.(V) -> V): Map<K, V> {
    val result = mutableMapOf<K, V>().apply { putAll(this@mergeMap) }
    overwrite.forEach { (k, v) ->
        val old = result[k]
        if (old != null) result[k] = old.merge(v)
        else result[k] = v
    }
    return result
}
