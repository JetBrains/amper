/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.api.Traceable
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
import org.jetbrains.amper.frontend.schema.ParcelizeSettings
import org.jetbrains.amper.frontend.schema.PublishingSettings
import org.jetbrains.amper.frontend.schema.SerializationSettings
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schema.TaskSettings
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

fun Base.mergeTemplate(overwrite: Base, target: () -> Base) =
    mergeNode(overwrite, target) {
        mergeNullableCollection(Base::repositories)
        mergeNodeProperty(Base::dependencies) { mergeListsMap(it) }
        mergeNodeProperty(Base::`test-dependencies`) { mergeListsMap(it) }
        mergeNodeProperty(Base::settings) { mergeMap(it) { overwriteSettings -> mergeSettings(overwriteSettings) } }
        mergeNodeProperty(Base::`test-settings`) { mergeMap(it) { overwriteSettings -> mergeSettings(overwriteSettings) } }
        mergeNodeProperty(Base::tasks) { mergeMap(it) { overwriteTaskSettings -> mergeTaskSettings(overwriteTaskSettings) } }
    }

context(MergeCtxWithProp<*, *>)
private fun TaskSettings.mergeTaskSettings(overwrite: TaskSettings): TaskSettings =
    mergeNode(overwrite, ::TaskSettings) {
        mergeNullableCollection(TaskSettings::dependsOn)
    }

fun Settings.mergeSettings(overwrite: Settings) =
    mergeNode(overwrite, ::Settings) {
        mergeNodeProperty(Settings::jvm, JvmSettings::mergeJvmSettings)
        mergeNodeProperty(Settings::android, AndroidSettings::mergeAndroidSettings)
        mergeNodeProperty(Settings::kotlin, KotlinSettings::mergeKotlinSettings)
        mergeNodeProperty(Settings::compose, ComposeSettings::mergeComposeSettings)
        mergeNodeProperty(Settings::kover, KoverSettings::mergeKoverSettings)
        mergeNodeProperty(Settings::ios, IosSettings::mergeIosSettings)
        mergeNodeProperty(Settings::publishing, PublishingSettings::mergePublishingSettings)
        mergeNodeProperty(Settings::native, NativeSettings::mergeNativeSettings)

        mergeScalar(Settings::junit)
    }

context(MergeCtxWithProp<*, *>)
private fun JvmSettings.mergeJvmSettings(overwrite: JvmSettings) =
    mergeNode(overwrite, ::JvmSettings) {
        mergeScalar(JvmSettings::release)
        mergeScalar(JvmSettings::mainClass)
    }

context(MergeCtxWithProp<*, *>)
private fun AndroidSettings.mergeAndroidSettings(overwrite: AndroidSettings) =
    mergeNode(overwrite, ::AndroidSettings) {
        mergeScalar(AndroidSettings::compileSdk)
        mergeScalar(AndroidSettings::minSdk)
        mergeScalar(AndroidSettings::maxSdk)
        mergeScalar(AndroidSettings::targetSdk)
        mergeScalar(AndroidSettings::applicationId)
        mergeScalar(AndroidSettings::namespace)
        mergeNodeProperty(AndroidSettings::signing, AndroidSigningSettings::mergeAndroidSigningSettings)
        mergeScalar(AndroidSettings::versionCode)
        mergeScalar(AndroidSettings::versionName)
        mergeNodeProperty(AndroidSettings::parcelize, ParcelizeSettings::mergeParcelizeSettings)
    }

context(MergeCtxWithProp<*, *>)
private fun AndroidSigningSettings.mergeAndroidSigningSettings(overwrite: AndroidSigningSettings) =
    mergeNode(overwrite, ::AndroidSigningSettings) {
        mergeScalar(AndroidSigningSettings::propertiesFile)
    }

context(MergeCtxWithProp<*, *>)
private fun KotlinSettings.mergeKotlinSettings(overwrite: KotlinSettings) =
    mergeNode(overwrite, ::KotlinSettings) {
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

        mergeNodeProperty(KotlinSettings::ksp, KspSettings::mergeKspSettings)
        mergeNodeProperty(KotlinSettings::serialization, SerializationSettings::mergeSerializationSettings)
    }

context(MergeCtxWithProp<*, *>)
private fun ComposeSettings.mergeComposeSettings(overwrite: ComposeSettings?) =
    mergeNode(overwrite, ::ComposeSettings) {
        mergeScalar(ComposeSettings::enabled)
        mergeScalar(ComposeSettings::version)
        mergeNodeProperty(ComposeSettings::resources, ComposeResourcesSettings::mergeComposeResourcesSettings)
    }

context(MergeCtxWithProp<*, *>)
private fun ParcelizeSettings.mergeParcelizeSettings(overwrite: ParcelizeSettings?) =
    mergeNode(overwrite, ::ParcelizeSettings) {
        mergeScalar(ParcelizeSettings::enabled)
        mergeCollection(ParcelizeSettings::additionalAnnotations)
    }

context(MergeCtxWithProp<*, *>)
private fun ComposeResourcesSettings.mergeComposeResourcesSettings(overwrite: ComposeResourcesSettings?) =
    mergeNode(overwrite, ::ComposeResourcesSettings) {
        mergeScalar(ComposeResourcesSettings::exposedAccessors)
        mergeScalar(ComposeResourcesSettings::packageName)
    }

context(MergeCtxWithProp<*, *>)
private fun KspSettings.mergeKspSettings(overwrite: KspSettings?) =
    mergeNode(overwrite, ::KspSettings) {
        mergeScalar(KspSettings::version)
        mergeCollection(KspSettings::processors)
        mergeNodeProperty(KspSettings::processorOptions) { mergeMap(it) { this } }
    }

context(MergeCtxWithProp<*, *>)
private fun SerializationSettings.mergeSerializationSettings(overwrite: SerializationSettings) =
    mergeNode(overwrite, ::SerializationSettings) {
        mergeScalar(SerializationSettings::enabled)
        mergeScalar(SerializationSettings::format)
        mergeScalar(SerializationSettings::version)
    }

context(MergeCtxWithProp<*, *>)
private fun IosSettings.mergeIosSettings(overwrite: IosSettings) =
    mergeNode(overwrite, ::IosSettings) {
        mergeScalar(IosSettings::teamId)
        mergeNodeProperty(IosSettings::framework, IosFrameworkSettings::mergeIosFrameworkSettings)
    }

context(MergeCtxWithProp<*, *>)
private fun PublishingSettings.mergePublishingSettings(overwrite: PublishingSettings) =
    mergeNode(overwrite, ::PublishingSettings) {
        mergeScalar(PublishingSettings::group)
        mergeScalar(PublishingSettings::version)
        mergeScalar(PublishingSettings::name)
    }

context(MergeCtxWithProp<*, *>)
private fun NativeSettings.mergeNativeSettings(overwrite: NativeSettings) =
    mergeNode(overwrite, ::NativeSettings) {
        mergeScalar(NativeSettings::entryPoint)
    }

context(MergeCtxWithProp<*, *>)
private fun IosFrameworkSettings.mergeIosFrameworkSettings(overwrite: IosFrameworkSettings) =
    mergeNode(overwrite, ::IosFrameworkSettings) {
        mergeScalar(IosFrameworkSettings::basename)
    }

context(MergeCtxWithProp<*, *>)
private fun KoverSettings.mergeKoverSettings(overwrite: KoverSettings) =
    mergeNode(overwrite, ::KoverSettings) {
        mergeScalar(KoverSettings::enabled)
        mergeNodeProperty(KoverSettings::xml, KoverXmlSettings::mergeKoverXmlSettings)
        mergeNodeProperty(KoverSettings::html, KoverHtmlSettings::mergeKoverHtmlSettings)
    }

context(MergeCtxWithProp<*, *>)
private fun KoverHtmlSettings.mergeKoverHtmlSettings(overwrite: KoverHtmlSettings) =
    mergeNode(overwrite, ::KoverHtmlSettings) {
        mergeScalar(KoverHtmlSettings::title)
        mergeScalar(KoverHtmlSettings::charset)
        mergeScalar(KoverHtmlSettings::onCheck)
        mergeScalar(KoverHtmlSettings::reportDir)
    }

context(MergeCtxWithProp<*, *>)
private fun KoverXmlSettings.mergeKoverXmlSettings(overwrite: KoverXmlSettings) =
    mergeNode(overwrite, ::KoverXmlSettings) {
        mergeScalar(KoverXmlSettings::onCheck)
        mergeScalar(KoverXmlSettings::reportFile)
    }

class MergeCtx<T : Any>(
    val target: T,
    val overwrite: T,
    val base: T,
)

class MergeCtxWithProp<T : Any, V>(ctx: MergeCtx<T>, prop: KProperty1<T, V>) {
    val targetProp = prop.valueBase(ctx.target)
    val baseProp = prop.valueBase(ctx.base)
    val overwriteProp = prop.valueBase(ctx.overwrite)
    val baseValue = baseProp?.withoutDefault
    val overwriteValue = overwriteProp?.withoutDefault
}

/**
 * [target] - accepted as lambda to evade non-necessary invocation.
 */
fun <T> T.mergeNode(
    overwrite: T,
    target: () -> T & Any,
    block: MergeCtx<T & Any>.() -> Unit
) = if (overwrite != null && this != null) {
    MergeCtx(target(), overwrite, this)
        .apply(block).target
        .apply { asSafely<Traceable>()?.trace = overwrite.asSafely<Traceable>()?.trace }
} else overwrite ?: this

fun <T : Any, V> MergeCtx<T>.mergeNullableCollection(
    prop: KMutableProperty1<T, List<V>?>,
) = doMergeCollection(prop) { this }

fun <T : Any, V> MergeCtx<T>.mergeCollection(
    prop: KMutableProperty1<T, List<V>>,
) = doMergeCollection(prop) { this }

private fun <T : Any, V, CV : List<V>?> MergeCtx<T>.doMergeCollection(
    prop: KMutableProperty1<T, CV>,
    toCV: List<V>.() -> CV?,
) = with(MergeCtxWithProp(this, prop)) {
    // TODO Handle collection merge tuning here.
    val targetProp = targetProp ?: return@with
    when {
        baseValue != null && overwriteValue != null ->
            targetProp(
                baseValue.toMutableList().apply { addAll(overwriteValue) }.toCV(),
                ValueBase.ValueState.MERGED,
                mergedTrace,
            )

        overwriteValue != null ->
            targetProp(overwriteValue, overwriteProp!!.state, explicitTrace)

        baseValue != null ->
            targetProp(baseValue, ValueBase.ValueState.INHERITED, inheritedTrace)
    }
}

fun <T : Any, V> MergeCtx<T>.mergeScalar(
    prop: KProperty1<T, V>
) = with(MergeCtxWithProp(this, prop)) {
    val targetProp = targetProp ?: return@with
    when {
        baseValue != null && overwriteValue != null ->
            targetProp(overwriteValue, ValueBase.ValueState.MERGED, mergedTrace)

        overwriteValue != null ->
            targetProp(overwriteValue, overwriteProp!!.state, explicitTrace)

        baseValue != null ->
            targetProp(baseValue, ValueBase.ValueState.INHERITED, inheritedTrace)
    }
}

fun <T : Any, V> MergeCtx<T>.mergeNodeProperty(
    prop: KProperty1<T, V>,
    doMerge: context(MergeCtxWithProp<T, V>) (V & Any).(V & Any) -> V,
) = with(MergeCtxWithProp(this, prop)) {
    val targetProp = targetProp ?: return@with
    when {
        baseValue != null && overwriteValue != null ->
            targetProp(doMerge.invoke(this@with, baseValue, overwriteValue), ValueBase.ValueState.MERGED, mergedTrace)

        baseValue == null && overwriteValue != null ->
            targetProp(overwriteValue, overwriteProp!!.state, explicitTrace)

        baseValue != null && overwriteValue == null ->
            targetProp(baseValue, ValueBase.ValueState.INHERITED, inheritedTrace)
    }
}

/** Trace from override value, with the preceding value as base. */
context(MergeCtxWithProp<T, V>, MergeCtx<*>) private val <T : Any, V> mergedTrace
    get() = overwriteProp!!.trace?.withPrecedingValue(precedingValue = baseProp)

/** Trace from override value, with no preceding value. */
context(MergeCtxWithProp<T, V>, MergeCtx<*>) private val <T : Any, V> explicitTrace
    get() = overwriteProp!!.trace

/** Trace from the base value, with no override. */
context(MergeCtxWithProp<T, V>, MergeCtx<*>) private val <T : Any, V> inheritedTrace
    get() = baseProp!!.trace

fun <K, V> Map<K, V>.mergeMap(overwrite: Map<K, V>?, merge: V.(V) -> V) =
    toMutableMap().apply { overwrite?.forEach { compute(it.key) { _, old -> old?.merge(it.value) ?: it.value } } }

fun <K, V> Map<K, List<V>>.mergeListsMap(overwrite: Map<K, List<V>>) =
    mergeMap(overwrite) { this + it }
