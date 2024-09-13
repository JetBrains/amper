/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.api.withPrecedingValue
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.AndroidSigningSettings
import org.jetbrains.amper.frontend.schema.Base
import org.jetbrains.amper.frontend.schema.ComposeResourcesSettings
import org.jetbrains.amper.frontend.schema.ComposeSettings
import org.jetbrains.amper.frontend.schema.IosFrameworkSettings
import org.jetbrains.amper.frontend.schema.IosSettings
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.KoverHtmlSettings
import org.jetbrains.amper.frontend.schema.KoverSettings
import org.jetbrains.amper.frontend.schema.KoverXmlSettings
import org.jetbrains.amper.frontend.schema.KspSettings
import org.jetbrains.amper.frontend.schema.NativeSettings
import org.jetbrains.amper.frontend.schema.PublishingSettings
import org.jetbrains.amper.frontend.schema.SerializationSettings
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schema.TaskSettings
import org.jetbrains.amper.frontend.schemaConverter.psi.yaml.TraceableMap
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1


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
    mergeNodeProperty(Base::dependencies) { mergeListsMap(it) }
    mergeNodeProperty(Base::`test-dependencies`) { mergeListsMap(it) }

    mergeNodeProperty(Base::settings) { mergeMap(it) { overwriteSettings -> merge(overwriteSettings) } }
    mergeNodeProperty(Base::`test-settings`) { mergeMap(it) { overwriteSettings -> merge(overwriteSettings) } }

    mergeNodeProperty(Base::tasks) { mergeMap(it) { overwriteTaskSettings -> merge(overwriteTaskSettings) } }
}

fun TaskSettings.merge(overwrite: TaskSettings): TaskSettings = mergeNode(overwrite, ::TaskSettings) {
    mergeNullableCollection(TaskSettings::dependsOn)
}

fun Settings.merge(overwrite: Settings) = mergeNode(overwrite, ::Settings) {
    mergeNodeProperty(Settings::jvm, JvmSettings::merge)
    mergeNodeProperty(Settings::android, AndroidSettings::merge)
    mergeNodeProperty(Settings::kotlin, KotlinSettings::merge)
    mergeNodeProperty(Settings::compose, ComposeSettings::merge)
    mergeNodeProperty(Settings::ksp, KspSettings::merge)
    mergeNodeProperty(Settings::kover, KoverSettings::merge)
    mergeNodeProperty(Settings::ios, IosSettings::merge)
    mergeNodeProperty(Settings::publishing, PublishingSettings::merge)
    mergeNodeProperty(Settings::native, NativeSettings::merge)

    mergeScalar(Settings::junit)
}

fun JvmSettings.merge(overwrite: JvmSettings) = mergeNode(overwrite, ::JvmSettings) {
    mergeScalar(JvmSettings::release)
    mergeScalar(JvmSettings::mainClass)
}

fun AndroidSettings.merge(overwrite: AndroidSettings) = mergeNode(overwrite, ::AndroidSettings) {
    mergeScalar(AndroidSettings::compileSdk)
    mergeScalar(AndroidSettings::minSdk)
    mergeScalar(AndroidSettings::maxSdk)
    mergeScalar(AndroidSettings::targetSdk)
    mergeScalar(AndroidSettings::applicationId)
    mergeScalar(AndroidSettings::namespace)
    mergeNodeProperty(AndroidSettings::signing, AndroidSigningSettings::merge)
}

fun AndroidSigningSettings.merge(overwrite: AndroidSigningSettings) = mergeNode(overwrite, ::AndroidSigningSettings) {
    mergeScalar(AndroidSigningSettings::propertiesFile)
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
    mergeScalar(ComposeSettings::version)
    mergeNodeProperty(ComposeSettings::resources, ComposeResourcesSettings::merge)
}

fun ComposeResourcesSettings.merge(overwrite: ComposeResourcesSettings?) =
    mergeNode(overwrite, ::ComposeResourcesSettings) {
        mergeScalar(ComposeResourcesSettings::exposedAccessors)
        mergeScalar(ComposeResourcesSettings::packageName)
        mergeScalar(ComposeResourcesSettings::enabled)
    }

fun KspSettings.merge(overwrite: KspSettings?) = mergeNode(overwrite, ::KspSettings) {
    mergeScalar(KspSettings::version)
    mergeCollection(KspSettings::processors)
    mergeNodeProperty(KspSettings::processorOptions) { mergeMap(it) { this } }
}

fun SerializationSettings.merge(overwrite: SerializationSettings) = mergeNode(overwrite, ::SerializationSettings) {
    mergeScalar(SerializationSettings::format)
}

fun IosSettings.merge(overwrite: IosSettings) = mergeNode(overwrite, ::IosSettings) {
    mergeScalar(IosSettings::teamId)
    mergeNodeProperty(IosSettings::framework, IosFrameworkSettings::merge)
}

fun PublishingSettings.merge(overwrite: PublishingSettings) = mergeNode(overwrite, ::PublishingSettings) {
    mergeScalar(PublishingSettings::group)
    mergeScalar(PublishingSettings::version)
    mergeScalar(PublishingSettings::name)
}

fun NativeSettings.merge(overwrite: NativeSettings) = mergeNode(overwrite, ::NativeSettings) {
    mergeScalar(NativeSettings::entryPoint)
}

fun IosFrameworkSettings.merge(overwrite: IosFrameworkSettings) = mergeNode(overwrite, ::IosFrameworkSettings) {
    mergeScalar(IosFrameworkSettings::basename)
}

fun KoverSettings.merge(overwrite: KoverSettings) = mergeNode(overwrite, ::KoverSettings) {
    mergeScalar(KoverSettings::enabled)
    mergeNodeProperty(KoverSettings::xml, KoverXmlSettings::merge)
    mergeNodeProperty(KoverSettings::html, KoverHtmlSettings::merge)
}

fun KoverHtmlSettings.merge(overwrite: KoverHtmlSettings) = mergeNode(overwrite, ::KoverHtmlSettings) {
    mergeScalar(KoverHtmlSettings::title)
    mergeScalar(KoverHtmlSettings::charset)
    mergeScalar(KoverHtmlSettings::onCheck)
    mergeScalar(KoverHtmlSettings::reportDir)
}

fun KoverXmlSettings.merge(overwrite: KoverXmlSettings) = mergeNode(overwrite, ::KoverXmlSettings) {
    mergeScalar(KoverXmlSettings::onCheck)
    mergeScalar(KoverXmlSettings::reportFile)
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
): T {
    return if (overwrite != null && this != null) {
        val builtTarget = target()
        MergeCtx(builtTarget, overwrite, this).apply(block).let { builtTarget }
    } else overwrite ?: this
}

fun <T : Any, V> MergeCtx<T>.mergeNullableCollection(
    prop: KMutableProperty1<T, List<V>?>,
) = doMergeCollection(prop) { this }

fun <T : Any, V> MergeCtx<T>.mergeCollection(
    prop: KMutableProperty1<T, List<V>>,
) = doMergeCollection(prop) { this }

private fun <T : Any, V, CV : List<V>?> MergeCtx<T>.doMergeCollection(
    prop: KMutableProperty1<T, CV>,
    toCV: List<V>.() -> CV,
) {
    // TODO Handle collection merge tuning here.
    val targetProp = prop.valueBase(target) ?: return
    val valueBase = prop.valueBase(base)
    val baseValue = valueBase?.withoutDefault
    val overwrite = prop.valueBase(overwrite)
    val overwriteValue = overwrite?.withoutDefault
    val result = baseValue?.toMutableList() ?: mutableListOf()
    result.addAll(overwriteValue ?: emptyList())
    if (targetProp.trace == null) {
        targetProp.trace = overwrite?.let { it.trace?.withPrecedingValue(valueBase) } ?: valueBase?.trace
    }
    targetProp(result.toCV())
}

fun <T : Any, V> MergeCtx<T>.mergeScalar(
    prop: KProperty1<T, V>
) {
    val targetProp = prop.valueBase(target) ?: return
    val baseProp = prop.valueBase(base)
    val baseValue = baseProp?.withoutDefault
    val overwriteProp = prop.valueBase(overwrite)
    val overwriteValue = overwriteProp?.withoutDefault
    when {
        overwriteValue != null -> targetProp(overwriteValue).withTraceFrom(overwriteProp, baseProp)
        baseValue != null -> targetProp(baseValue).withTraceFrom(baseProp)
    }
}

fun <T : Any, V> MergeCtx<T>.mergeNodeProperty(
    prop: KProperty1<T, V>,
    doMerge: (V & Any).(V & Any) -> V,
) = apply {
    val targetProp = prop.valueBase(target) ?: return@apply
    val baseProp = prop.valueBase(base)
    val baseValue = baseProp?.withoutDefault
    val overwriteProp = prop.valueBase(overwrite)
    val overwriteValue = overwriteProp?.withoutDefault
    when {
        baseValue != null && overwriteValue != null -> targetProp(baseValue.doMerge(overwriteValue))
            .withTraceFrom(if (overwriteProp.withoutDefault != null) overwriteProp else baseProp,
                overwriteProp.takeIf { it.withoutDefault != null })
        baseValue != null && overwriteValue == null -> targetProp(baseValue).withTraceFrom(baseProp)
        baseValue == null && overwriteValue != null -> targetProp(overwriteValue).withTraceFrom(overwriteProp)
        else -> Unit
    }
}

private fun <V> ValueBase<V>.withTraceFrom(
    traceSource: ValueBase<V>,
    overridesFrom: ValueBase<V>? = null
): ValueBase<V> = this.also {
    trace = traceSource.trace?.withPrecedingValue(overridesFrom)
}

fun <K, V> Map<K, V>.mergeMap(overwrite: Map<K, V>?, merge: V.(V) -> V) = TraceableMap<K, V>().apply {
    putAll(this@mergeMap)
    overwrite?.forEach { (k, v) ->
        val old = this[k]
        if (old != null) this[k] = old.merge(v)
        else this[k] = v
    }
}

fun <K, V> Map<K, List<V>>.mergeListsMap(overwrite: Map<K, List<V>>) =
    mergeMap(overwrite) { this + it }
