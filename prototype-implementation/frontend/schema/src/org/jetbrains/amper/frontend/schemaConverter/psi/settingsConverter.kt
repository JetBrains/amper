/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.schema.*
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar

context(ProblemReporterContext)
internal fun YAMLMapping.convertSettings() = Settings().apply {
    // TODO Report wrong node types.
    java(tryGetMappingNode("java")?.convertJavaSettings())
    jvm(tryGetMappingNode("jvm")?.convertJvmSettings())
    android(tryGetMappingNode("android")?.convertAndroidSettings())
    kotlin(tryGetMappingNode("kotlin")?.convertKotlinSettings())
    compose(tryGetChildNode("compose")?.convertComposeSettings())
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