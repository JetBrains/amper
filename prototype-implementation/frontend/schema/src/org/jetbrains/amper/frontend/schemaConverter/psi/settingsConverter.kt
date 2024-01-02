/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.schema.*
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.jetbrains.yaml.psi.YAMLScalar

context(ProblemReporterContext)
internal fun YAMLPsiElement.convertSettings() = assertNodeType<YAMLMapping, Settings>("settings") {
    doConvertSettings()
}?.adjustTrace(this)

context(ProblemReporterContext)
internal fun YAMLMapping.doConvertSettings() = Settings().apply {
    // TODO Report wrong node types.
    java(tryGetMappingNode("java")?.convertJavaSettings())
    jvm(tryGetMappingNode("jvm")?.convertJvmSettings())
    android(tryGetMappingNode("android")?.convertAndroidSettings())
    kotlin(tryGetMappingNode("kotlin")?.convertKotlinSettings())
    compose(tryGetChildNode("compose")?.convertComposeSettings())
    ios(tryGetMappingNode("ios")?.convertIosSettings())
    publishing(tryGetMappingNode("publishing")?.convertPublishingSettings())
    kover(tryGetMappingNode("kover")?.convertKoverSettings())
}

context(ProblemReporterContext)
internal fun YAMLMapping.convertJavaSettings() = JavaSettings().apply {
    source(tryGetScalarNode("source"))
}

context(ProblemReporterContext)
internal fun YAMLMapping.convertJvmSettings() = JvmSettings().apply {
    target(tryGetScalarNode("target"))
    mainClass(tryGetScalarNode("mainClass"))
}.adjustTrace(this)

context(ProblemReporterContext)
internal fun YAMLMapping.convertAndroidSettings() = AndroidSettings().apply {
    compileSdk(tryGetScalarNode("compileSdk"))
    minSdk(tryGetScalarNode("minSdk"))
    maxSdk(tryGetScalarNode("maxSdk"))
    targetSdk(tryGetScalarNode("targetSdk"))
    applicationId(tryGetScalarNode("applicationId"))
    namespace(tryGetScalarNode("namespace"))
}.adjustTrace(this)

context(ProblemReporterContext)
internal fun YAMLMapping.convertKotlinSettings() = KotlinSettings().apply {
    // TODO Report wrong types.
    languageVersion(tryGetScalarNode("languageVersion"))
    apiVersion(tryGetScalarNode("apiVersion"))

    allWarningsAsErrors(tryGetScalarNode("allWarningsAsErrors"))
    suppressWarnings(tryGetScalarNode("suppressWarnings"))
    verbose(tryGetScalarNode("verbose"))
    debug(tryGetScalarNode("debug"))
    progressiveMode(tryGetScalarNode("progressiveMode"))

    freeCompilerArgs(tryGetScalarSequenceNode("freeCompilerArgs"))
    linkerOpts(tryGetScalarSequenceNode("linkerOpts"))
    languageFeatures(tryGetScalarSequenceNode("languageFeatures"))
    optIns(tryGetScalarSequenceNode("optIns"))

    serialization(tryGetChildNode("serialization")?.convertSerializationSettings())
}.adjustTrace(this@convertKotlinSettings)

context(ProblemReporterContext)
internal fun YAMLKeyValue.convertSerializationSettings() = when (value) {
    is YAMLScalar -> SerializationSettings().apply { engine(value as YAMLScalar) }
    is YAMLMapping -> SerializationSettings().apply { engine((value as YAMLMapping).tryGetScalarNode("engine")) }
    else -> null
}?.adjustTrace(this)

context(ProblemReporterContext)
internal fun YAMLKeyValue.convertComposeSettings() = when (value) {
    // TODO Report wrong value.
    is YAMLScalar -> ComposeSettings().apply { enabled(valueText == "enabled").adjustTrace(value) }
    is YAMLMapping -> ComposeSettings().apply { enabled((value as YAMLMapping).tryGetScalarNode("enabled")) }
    else -> null
}?.adjustTrace(this)

context(ProblemReporterContext)
internal fun YAMLMapping.convertIosSettings() = IosSettings().apply {
    teamId(tryGetScalarNode("teamId"))
    framework(tryGetChildNode("framework")?.value?.asMappingNode()?.convertIosFrameworkSettings())
}.adjustTrace(this)

context(ProblemReporterContext)
internal fun YAMLMapping.convertIosFrameworkSettings() = IosFrameworkSettings().apply {
    basename(tryGetScalarNode("basename"))
    isStatic(tryGetScalarNode("isStatic"))
    // TODO Report wrong types/values.
    mappings(convertScalarKeyedMap {key ->
        (this as? YAMLScalar)?.textValue.takeIf { key != "basename" && key != "isStatic" }
    })
}.adjustTrace(this)

context(ProblemReporterContext)
internal fun YAMLMapping.convertPublishingSettings() = PublishingSettings().apply {
    group(tryGetScalarNode("group"))
    version(tryGetScalarNode("version"))
}.adjustTrace(this)

context(ProblemReporterContext)
internal fun YAMLMapping.convertKoverSettings() = KoverSettings().apply {
    enabled(tryGetScalarNode("enabled"))
    xml(tryGetMappingNode("xml")?.convertKoverXmlSettings())
    html(tryGetMappingNode("html")?.convertKoverHtmlSettings())
}.adjustTrace(this)

context(ProblemReporterContext)
internal fun YAMLMapping.convertKoverXmlSettings() = KoverXmlSettings().apply {
    onCheck(tryGetScalarNode("onCheck"))
    reportFile(tryGetScalarNode("reportFile"))
}.adjustTrace(this)

context(ProblemReporterContext)
internal fun YAMLMapping.convertKoverHtmlSettings() = KoverHtmlSettings().apply {
    onCheck(tryGetScalarNode("onCheck"))
    title(tryGetScalarNode("title"))
    charset(tryGetScalarNode("charset"))
    reportDir(tryGetScalarNode("reportDir"))
}.adjustTrace(this)