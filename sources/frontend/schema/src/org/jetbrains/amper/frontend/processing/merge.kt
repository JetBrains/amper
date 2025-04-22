/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.AndroidSigningSettings
import org.jetbrains.amper.frontend.schema.Base
import org.jetbrains.amper.frontend.schema.ComposeExperimentalHotReloadSettings
import org.jetbrains.amper.frontend.schema.ComposeExperimentalSettings
import org.jetbrains.amper.frontend.schema.ComposeResourcesSettings
import org.jetbrains.amper.frontend.schema.ComposeSettings
import org.jetbrains.amper.frontend.schema.IosFrameworkSettings
import org.jetbrains.amper.frontend.schema.IosSettings
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.JvmTestSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.KoverHtmlSettings
import org.jetbrains.amper.frontend.schema.KoverSettings
import org.jetbrains.amper.frontend.schema.KoverXmlSettings
import org.jetbrains.amper.frontend.schema.KspSettings
import org.jetbrains.amper.frontend.schema.KtorSettings
import org.jetbrains.amper.frontend.schema.SpringBootSettings
import org.jetbrains.amper.frontend.schema.NativeSettings
import org.jetbrains.amper.frontend.schema.ParcelizeSettings
import org.jetbrains.amper.frontend.schema.PublishingSettings
import org.jetbrains.amper.frontend.schema.SerializationSettings
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schema.TaskSettings
import org.jetbrains.amper.frontend.schema.NoArgSettings
import org.jetbrains.amper.frontend.schema.AllOpenSettings


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
        mergeNullableCollectionProperty(Base::repositories)
        mergeProperty(Base::dependencies) { mergeListsMap(it) }
        mergeProperty(Base::`test-dependencies`) { mergeListsMap(it) }
        mergeProperty(Base::settings) { mergeMap(it) { overwriteSettings -> mergeSettings(overwriteSettings) } }
        mergeProperty(Base::`test-settings`) { mergeMap(it) { overwriteSettings -> mergeSettings(overwriteSettings) } }
        mergeProperty(Base::tasks) { mergeMap(it) { overwriteTaskSettings -> mergeTaskSettings(overwriteTaskSettings) } }
    }

context(MergeCtxWithProp<*, *>)
private fun NoArgSettings.mergeNoArgSettings(overwrite: NoArgSettings?) =
    mergeNode(overwrite, ::NoArgSettings) {
        mergeScalarProperty(NoArgSettings::enabled)
        mergeNullableCollectionProperty(NoArgSettings::annotations)
        mergeScalarProperty(NoArgSettings::invokeInitializers)
        mergeNullableCollectionProperty(NoArgSettings::presets)
    }

context(MergeCtxWithProp<*, *>)
private fun AllOpenSettings.mergeAllOpenSettings(overwrite: AllOpenSettings?) =
    mergeNode(overwrite, ::AllOpenSettings) {
        mergeScalarProperty(AllOpenSettings::enabled)
        mergeNullableCollectionProperty(AllOpenSettings::annotations)
        mergeNullableCollectionProperty(AllOpenSettings::presets)
    }

context(MergeCtxWithProp<*, *>)
private fun TaskSettings.mergeTaskSettings(overwrite: TaskSettings): TaskSettings =
    mergeNode(overwrite, ::TaskSettings) {
        mergeNullableCollectionProperty(TaskSettings::dependsOn)
    }

fun Settings.mergeSettings(overwrite: Settings) =
    mergeNode(overwrite, ::Settings) {
        mergeProperty(Settings::jvm, JvmSettings::mergeJvmSettings)
        mergeProperty(Settings::android, AndroidSettings::mergeAndroidSettings)
        mergeProperty(Settings::kotlin, KotlinSettings::mergeKotlinSettings)
        mergeProperty(Settings::compose, ComposeSettings::mergeComposeSettings)
        mergeProperty(Settings::kover, KoverSettings::mergeKoverSettings)
        mergeProperty(Settings::ios, IosSettings::mergeIosSettings)
        mergeProperty(Settings::publishing, PublishingSettings::mergePublishingSettings)
        mergeProperty(Settings::native, NativeSettings::mergeNativeSettings)
        mergeProperty(Settings::ktor, KtorSettings::mergeKtorServerSettings)
        mergeProperty(Settings::springBoot, SpringBootSettings::mergeSpringBootSettings)

        mergeScalarProperty(Settings::junit)
    }

context(MergeCtxWithProp<*, *>)
private fun JvmSettings.mergeJvmSettings(overwrite: JvmSettings) =
    mergeNode(overwrite, ::JvmSettings) {
        mergeScalarProperty(JvmSettings::release)
        mergeScalarProperty(JvmSettings::mainClass)
        mergeProperty(JvmSettings::test, JvmTestSettings::mergeJvmTestSettings)
    }

context(MergeCtxWithProp<*, *>)
private fun JvmTestSettings.mergeJvmTestSettings(overwrite: JvmTestSettings) =
    mergeNode(overwrite, ::JvmTestSettings) {
        mergeProperty(JvmTestSettings::systemProperties) { mergeMap(it) { this } }
        mergeCollectionProperty(JvmTestSettings::freeJvmArgs)
    }

context(MergeCtxWithProp<*, *>)
private fun AndroidSettings.mergeAndroidSettings(overwrite: AndroidSettings) =
    mergeNode(overwrite, ::AndroidSettings) {
        mergeScalarProperty(AndroidSettings::compileSdk)
        mergeScalarProperty(AndroidSettings::minSdk)
        mergeScalarProperty(AndroidSettings::maxSdk)
        mergeScalarProperty(AndroidSettings::targetSdk)
        mergeScalarProperty(AndroidSettings::applicationId)
        mergeScalarProperty(AndroidSettings::namespace)
        mergeProperty(AndroidSettings::signing, AndroidSigningSettings::mergeAndroidSigningSettings)
        mergeScalarProperty(AndroidSettings::versionCode)
        mergeScalarProperty(AndroidSettings::versionName)
        mergeProperty(AndroidSettings::parcelize, ParcelizeSettings::mergeParcelizeSettings)
    }

context(MergeCtxWithProp<*, *>)
private fun AndroidSigningSettings.mergeAndroidSigningSettings(overwrite: AndroidSigningSettings) =
    mergeNode(overwrite, ::AndroidSigningSettings) {
        mergeScalarProperty(AndroidSigningSettings::propertiesFile)
    }

context(MergeCtxWithProp<*, *>)
private fun KotlinSettings.mergeKotlinSettings(overwrite: KotlinSettings) =
    mergeNode(overwrite, ::KotlinSettings) {
        mergeScalarProperty(KotlinSettings::languageVersion)
        mergeScalarProperty(KotlinSettings::apiVersion)
        mergeScalarProperty(KotlinSettings::allWarningsAsErrors)
        mergeScalarProperty(KotlinSettings::suppressWarnings)
        mergeScalarProperty(KotlinSettings::verbose)
        mergeScalarProperty(KotlinSettings::debug)
        mergeScalarProperty(KotlinSettings::progressiveMode)

        mergeNullableCollectionProperty(KotlinSettings::freeCompilerArgs)
        mergeNullableCollectionProperty(KotlinSettings::linkerOpts)
        mergeNullableCollectionProperty(KotlinSettings::languageFeatures)
        mergeNullableCollectionProperty(KotlinSettings::optIns)

        mergeProperty(KotlinSettings::ksp, KspSettings::mergeKspSettings)
        mergeProperty(KotlinSettings::serialization, SerializationSettings::mergeSerializationSettings)
        mergeProperty(KotlinSettings::noArg, NoArgSettings::mergeNoArgSettings)
        mergeProperty(KotlinSettings::allOpen, AllOpenSettings::mergeAllOpenSettings)
    }

context(MergeCtxWithProp<*, *>)
private fun ComposeSettings.mergeComposeSettings(overwrite: ComposeSettings?) =
    mergeNode(overwrite, ::ComposeSettings) {
        mergeScalarProperty(ComposeSettings::enabled)
        mergeScalarProperty(ComposeSettings::version)
        mergeProperty(ComposeSettings::resources, ComposeResourcesSettings::mergeComposeResourcesSettings)
        mergeProperty(ComposeSettings::experimental, ComposeExperimentalSettings::mergeComposeExperimentalSettings)
    }

context(MergeCtxWithProp<*, *>)
fun ComposeExperimentalSettings.mergeComposeExperimentalSettings(overwrite: ComposeExperimentalSettings?) =
    mergeNode(overwrite, ::ComposeExperimentalSettings) {
        mergeProperty(ComposeExperimentalSettings::hotReload, ComposeExperimentalHotReloadSettings::mergeComposeExperimentalHotReloadSettings)
    }

context(MergeCtxWithProp<*, *>)
fun ComposeExperimentalHotReloadSettings.mergeComposeExperimentalHotReloadSettings(overwrite: ComposeExperimentalHotReloadSettings?) = mergeNode(overwrite, ::ComposeExperimentalHotReloadSettings) {
    mergeScalarProperty(ComposeExperimentalHotReloadSettings::enabled)
}

context(MergeCtxWithProp<*, *>)
private fun ParcelizeSettings.mergeParcelizeSettings(overwrite: ParcelizeSettings?) =
    mergeNode(overwrite, ::ParcelizeSettings) {
        mergeScalarProperty(ParcelizeSettings::enabled)
        mergeCollectionProperty(ParcelizeSettings::additionalAnnotations)
    }

context(MergeCtxWithProp<*, *>)
private fun ComposeResourcesSettings.mergeComposeResourcesSettings(overwrite: ComposeResourcesSettings?) =
    mergeNode(overwrite, ::ComposeResourcesSettings) {
        mergeScalarProperty(ComposeResourcesSettings::exposedAccessors)
        mergeScalarProperty(ComposeResourcesSettings::packageName)
    }

context(MergeCtxWithProp<*, *>)
private fun KspSettings.mergeKspSettings(overwrite: KspSettings?) =
    mergeNode(overwrite, ::KspSettings) {
        mergeScalarProperty(KspSettings::version)
        mergeCollectionProperty(KspSettings::processors)
        mergeProperty(KspSettings::processorOptions) { mergeMap(it) { this } }
    }

context(MergeCtxWithProp<*, *>)
private fun SerializationSettings.mergeSerializationSettings(overwrite: SerializationSettings) =
    mergeNode(overwrite, ::SerializationSettings) {
        mergeScalarProperty(SerializationSettings::enabled)
        mergeScalarProperty(SerializationSettings::format)
        mergeScalarProperty(SerializationSettings::version)
    }

context(MergeCtxWithProp<*, *>)
private fun IosSettings.mergeIosSettings(overwrite: IosSettings) =
    mergeNode(overwrite, ::IosSettings) {
        mergeScalarProperty(IosSettings::teamId)
        mergeProperty(IosSettings::framework, IosFrameworkSettings::mergeIosFrameworkSettings)
    }

context(MergeCtxWithProp<*, *>)
private fun PublishingSettings.mergePublishingSettings(overwrite: PublishingSettings) =
    mergeNode(overwrite, ::PublishingSettings) {
        mergeScalarProperty(PublishingSettings::group)
        mergeScalarProperty(PublishingSettings::version)
        mergeScalarProperty(PublishingSettings::name)
    }

context(MergeCtxWithProp<*, *>)
private fun NativeSettings.mergeNativeSettings(overwrite: NativeSettings) =
    mergeNode(overwrite, ::NativeSettings) {
        mergeScalarProperty(NativeSettings::entryPoint)
    }

context(MergeCtxWithProp<*, *>)
private fun IosFrameworkSettings.mergeIosFrameworkSettings(overwrite: IosFrameworkSettings) =
    mergeNode(overwrite, ::IosFrameworkSettings) {
        mergeScalarProperty(IosFrameworkSettings::basename)
    }

context(MergeCtxWithProp<*, *>)
private fun KoverSettings.mergeKoverSettings(overwrite: KoverSettings) =
    mergeNode(overwrite, ::KoverSettings) {
        mergeScalarProperty(KoverSettings::enabled)
        mergeProperty(KoverSettings::xml, KoverXmlSettings::mergeKoverXmlSettings)
        mergeProperty(KoverSettings::html, KoverHtmlSettings::mergeKoverHtmlSettings)
    }

context(MergeCtxWithProp<*, *>)
private fun KoverHtmlSettings.mergeKoverHtmlSettings(overwrite: KoverHtmlSettings) =
    mergeNode(overwrite, ::KoverHtmlSettings) {
        mergeScalarProperty(KoverHtmlSettings::title)
        mergeScalarProperty(KoverHtmlSettings::charset)
        mergeScalarProperty(KoverHtmlSettings::onCheck)
        mergeScalarProperty(KoverHtmlSettings::reportDir)
    }

context(MergeCtxWithProp<*, *>)
private fun KtorSettings.mergeKtorServerSettings(overwrite: KtorSettings) =
    mergeNode(overwrite, ::KtorSettings) {
        mergeScalarProperty(KtorSettings::enabled)
        mergeScalarProperty(KtorSettings::version)
    }

context(MergeCtxWithProp<*, *>)
private fun KoverXmlSettings.mergeKoverXmlSettings(overwrite: KoverXmlSettings) =
    mergeNode(overwrite, ::KoverXmlSettings) {
        mergeScalarProperty(KoverXmlSettings::onCheck)
        mergeScalarProperty(KoverXmlSettings::reportFile)
    }

context(MergeCtxWithProp<*, *>)
private fun SpringBootSettings.mergeSpringBootSettings(overwrite: SpringBootSettings) =
    mergeNode(overwrite, ::SpringBootSettings) {
        mergeScalarProperty(SpringBootSettings::enabled)
        mergeScalarProperty(SpringBootSettings::version)
    }