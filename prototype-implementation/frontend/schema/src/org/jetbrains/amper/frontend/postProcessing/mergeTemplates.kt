/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.postProcessing

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.Base
import org.jetbrains.amper.frontend.schema.ComposeSettings
import org.jetbrains.amper.frontend.schema.JavaSettings
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.SerializationSettings
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.schemaConverter.convertTemplate
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
val file_doc = Unit

context(ProblemReporterContext)
fun Module.readTemplatesAndMerge(
    reader: (Path) -> Template = { convertTemplate(it) }
): Module {
    val readTemplates = apply.value?.map(reader) ?: emptyList()
    val toMerge = readTemplates + this
    // We are sure that last instance is a Module, so we can cast.
    return toMerge.reduce { first, second -> /* TODO: Maybe create new for each merge */first.merge(second, second) } as Module
}

fun <T : Base> Base.merge(
    overwrite: Base,
    target: T,
): T = mergeNode(overwrite, target) {
    mergeCollection(Base::repositories)
    mergeNodeProperty(Base::dependencies) { this + it }
    mergeNodeProperty(Base::`test-dependencies`) { this + it }

    mergeNodeProperty(Base::settings) { mergeMap(it) { overwriteSettings -> merge(overwriteSettings) } }
    mergeNodeProperty(Base::`test-settings`) { mergeMap(it) { overwriteSettings -> merge(overwriteSettings) } }
}

fun Settings.merge(overwrite: Settings) = mergeNode(overwrite, Settings()) {
    mergeNodeProperty(Settings::java, JavaSettings::merge)
    mergeNodeProperty(Settings::jvm, JvmSettings::merge)
    mergeNodeProperty(Settings::android, AndroidSettings::merge)
    mergeNodeProperty(Settings::kotlin, KotlinSettings::merge)
    mergeNodeProperty(Settings::compose, ComposeSettings::merge)
}

fun JavaSettings.merge(overwrite: JavaSettings) = mergeNode(overwrite, JavaSettings()) {
    mergeScalar(JavaSettings::source)
}

fun JvmSettings.merge(overwrite: JvmSettings) = mergeNode(overwrite, JvmSettings()) {
    mergeScalar(JvmSettings::target)
    mergeScalar(JvmSettings::mainClass)
}

fun AndroidSettings.merge(overwrite: AndroidSettings) = mergeNode(overwrite, AndroidSettings()) {
    mergeScalar(AndroidSettings::compileSdk)
    mergeScalar(AndroidSettings::minSdk)
    mergeScalar(AndroidSettings::maxSdk)
    mergeScalar(AndroidSettings::targetSdk)
    mergeScalar(AndroidSettings::applicationId)
    mergeScalar(AndroidSettings::namespace)
}

fun KotlinSettings.merge(overwrite: KotlinSettings) = mergeNode(overwrite, KotlinSettings()) {
    mergeScalar(KotlinSettings::languageVersion)
    mergeScalar(KotlinSettings::apiVersion)
    mergeScalar(KotlinSettings::allWarningsAsErrors)
    mergeScalar(KotlinSettings::suppressWarnings)
    mergeScalar(KotlinSettings::verbose)
    mergeScalar(KotlinSettings::debug)
    mergeScalar(KotlinSettings::progressiveMode)

    mergeCollection(KotlinSettings::freeCompilerArgs)
    mergeCollection(KotlinSettings::linkerOpts)
    mergeCollection(KotlinSettings::languageFeatures)
    mergeCollection(KotlinSettings::optIns)

    mergeNodeProperty(KotlinSettings::serialization, SerializationSettings::merge)
}

fun ComposeSettings.merge(overwrite: ComposeSettings) = mergeNode(overwrite, ComposeSettings()) {
    mergeScalar(ComposeSettings::enabled)
}

fun SerializationSettings.merge(overwrite: SerializationSettings) = mergeNode(overwrite, SerializationSettings()) {
    mergeScalar(SerializationSettings::engine)
}

data class MergeCtx<T>(
    val target: T,
    val overwrite: T,
    val base: T,
)

fun <MergeT, TargetT : MergeT> MergeT.mergeNode(
    overwrite: MergeT,
    target: TargetT,
    block: MergeCtx<MergeT>.() -> Unit
): TargetT = MergeCtx(target, overwrite, this).apply(block).let { target }

/**
 * Shortcut for merging collection property.
 */
fun <T, V> MergeCtx<T>.mergeCollection(prop: T.() -> ValueBase<List<V>>) {
    // TODO Handle collection merge tuning here.
    val targetProp = target.prop()
    val baseValue = base.prop().orNull
    val overwriteValue = overwrite.prop().orNull
    val result = baseValue?.toMutableList() ?: mutableListOf()
    result.addAll(overwriteValue ?: emptyList())
    targetProp(result)
}

/**
 * Shortcut for merging scalar property.
 */
fun <T, V> MergeCtx<T>.mergeScalar(prop: T.() -> ValueBase<V>) {
    val targetProp = target.prop()
    val baseValue = base.prop().orNull
    val overwriteValue = overwrite.prop().orNull
    targetProp(overwriteValue ?: baseValue)
}

/**
 * Shortcut for merging [ValueBase] property.
 */
fun <T, V> MergeCtx<T>.mergeNodeProperty(
    prop: T.() -> ValueBase<V>,
    doMerge: V.(V) -> V,
) = apply {
    val targetProp = target.prop()
    val baseValue = base.prop().orNull
    val overwriteValue = overwrite.prop().orNull
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
