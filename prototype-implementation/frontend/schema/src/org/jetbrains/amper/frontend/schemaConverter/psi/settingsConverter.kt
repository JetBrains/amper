/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.assertNodeType
import org.jetbrains.amper.frontend.schema.*
import org.jetbrains.amper.frontend.schemaConverter.*
import org.jetbrains.amper.frontend.schemaConverter.psi.convertAndroidSettings
import org.jetbrains.amper.frontend.schemaConverter.psi.convertComposeSettings
import org.jetbrains.amper.frontend.schemaConverter.psi.convertIosSettings
import org.jetbrains.amper.frontend.schemaConverter.psi.convertJavaSettings
import org.jetbrains.amper.frontend.schemaConverter.psi.convertJvmSettings
import org.jetbrains.amper.frontend.schemaConverter.psi.convertKotlinSettings
import org.jetbrains.amper.frontend.schemaConverter.psi.convertKoverSettings
import org.jetbrains.amper.frontend.schemaConverter.psi.convertPublishingSettings
import org.jetbrains.amper.frontend.schemaConverter.psi.convertSerializationSettings
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.jetbrains.yaml.psi.YAMLScalar
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node

context(ProblemReporterContext)
internal fun YAMLPsiElement.convertSettings() = assertNodeType<YAMLMapping, Settings>("settings") {
    doConvertSettings()
}

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
}

context(ProblemReporterContext)
internal fun YAMLMapping.convertAndroidSettings() = AndroidSettings().apply {
    compileSdk(tryGetScalarNode("compileSdk"))
    minSdk(tryGetScalarNode("minSdk"))
    maxSdk(tryGetScalarNode("maxSdk"))
    targetSdk(tryGetScalarNode("targetSdk"))
    applicationId(tryGetScalarNode("applicationId"))
    namespace(tryGetScalarNode("namespace"))
}

context(ProblemReporterContext)
internal fun YAMLMapping.convertKotlinSettings() = KotlinSettings().apply {
    // TODO Report wrong types.
    languageVersion(tryGetScalarNode("languageVersion")?.textValue)
    apiVersion(tryGetScalarNode("apiVersion")?.textValue)

    allWarningsAsErrors(tryGetScalarNode("allWarningsAsErrors")?.textValue?.toBoolean())
    suppressWarnings(tryGetScalarNode("suppressWarnings")?.textValue?.toBoolean())
    verbose(tryGetScalarNode("verbose")?.textValue?.toBoolean())
    debug(tryGetScalarNode("debug")?.textValue?.toBoolean())
    progressiveMode(tryGetScalarNode("progressiveMode")?.textValue?.toBoolean())

    freeCompilerArgs(tryGetScalarSequenceNode("freeCompilerArgs")?.values())
    linkerOpts(tryGetScalarSequenceNode("linkerOpts")?.values())
    languageFeatures(tryGetScalarSequenceNode("languageFeatures")?.values())
    optIns(tryGetScalarSequenceNode("optIns")?.values())

    serialization(tryGetChildNode("serialization")?.convertSerializationSettings())
}

context(ProblemReporterContext)
internal fun YAMLKeyValue.convertSerializationSettings() = when (value) {
    is YAMLScalar -> SerializationSettings().apply { engine(valueText) }
    is YAMLMapping -> SerializationSettings().apply { engine((value as YAMLMapping).tryGetScalarNode("engine")) }
    else -> null
}

context(ProblemReporterContext)
internal fun YAMLKeyValue.convertComposeSettings() = when (value) {
    // TODO Report wrong value.
    is YAMLScalar -> ComposeSettings().apply { enabled(valueText == "enabled") }
    is YAMLMapping -> ComposeSettings().apply { enabled((value as YAMLMapping).tryGetScalarNode("enabled")?.textValue?.toBoolean()) }
    else -> null
}

context(ProblemReporterContext)
internal fun YAMLMapping.convertIosSettings() = IosSettings().apply {
    teamId(tryGetScalarNode("teamId")?.textValue)
    framework(tryGetChildNode("framework")?.value?.asMappingNode()?.convertIosFrameworkSettings())
}

context(ProblemReporterContext)
internal fun YAMLMapping.convertIosFrameworkSettings() = IosFrameworkSettings().apply {
    basename(tryGetScalarNode("basename")?.textValue)
    isStatic(tryGetScalarNode("isStatic")?.textValue?.toBoolean())
    // TODO Report wrong types/values.
    mappings(convertScalarKeyedMap {key ->
        (this as? YAMLScalar)?.textValue.takeIf { key != "basename" && key != "isStatic" }
    })
}

context(ProblemReporterContext)
internal fun YAMLMapping.convertPublishingSettings() = PublishingSettings().apply {
    group(tryGetScalarNode("group"))
    version(tryGetScalarNode("version"))
}

context(ProblemReporterContext)
internal fun YAMLMapping.convertKoverSettings() = KoverSettings().apply {
    enabled(tryGetScalarNode("enabled")?.textValue?.toBoolean()) // TODO Check type
    xml(tryGetMappingNode("xml")?.convertKoverXmlSettings())
    html(tryGetMappingNode("html")?.convertKoverHtmlSettings())
}

context(ProblemReporterContext)
internal fun YAMLMapping.convertKoverXmlSettings() = KoverXmlSettings().apply {
    onCheck(tryGetScalarNode("onCheck")?.textValue?.toBoolean()) // TODO Check type
    reportFile(tryGetScalarNode("reportFile"))
}

context(ProblemReporterContext)
internal fun YAMLMapping.convertKoverHtmlSettings() = KoverHtmlSettings().apply {
    onCheck(tryGetScalarNode("onCheck")?.textValue?.toBoolean()) // TODO Check type
    title(tryGetScalarNode("title"))
    charset(tryGetScalarNode("charset"))
    reportDir(tryGetScalarNode("reportDir"))
}