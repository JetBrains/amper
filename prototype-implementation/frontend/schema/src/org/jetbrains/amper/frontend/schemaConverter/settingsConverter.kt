/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.schema.*
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode

context(ProblemReporterContext)
internal fun MappingNode.convertSettings() = Settings().apply {
    // TODO Report wrong node types.
    java(tryGetChildNode("java")?.asMappingNode()?.convertJavaSettings())
    jvm(tryGetChildNode("jvm")?.asMappingNode()?.convertJvmSettings())
    android(tryGetChildNode("android")?.asMappingNode()?.convertAndroidSettings())
    kotlin(tryGetChildNode("kotlin")?.asMappingNode()?.convertKotlinSettings())
    compose(tryGetChildNode("compose")?.convertComposeSettings())
}

context(ProblemReporterContext)
internal fun MappingNode.convertJavaSettings() = JavaSettings().apply {
    source(tryGetScalarNode("source"))
}

context(ProblemReporterContext)
internal fun MappingNode.convertJvmSettings() = JvmSettings().apply {
    target(tryGetScalarNode("target"))
    mainClass(tryGetScalarNode("mainClass"))
}

context(ProblemReporterContext)
internal fun MappingNode.convertAndroidSettings() = AndroidSettings().apply {
    compileSdk(tryGetScalarNode("compileSdk"))
    minSdk(tryGetScalarNode("minSdk"))
    maxSdk(tryGetScalarNode("maxSdk"))
    targetSdk(tryGetScalarNode("targetSdk"))
    applicationId(tryGetScalarNode("applicationId"))
    namespace(tryGetScalarNode("namespace"))
}

context(ProblemReporterContext)
internal fun MappingNode.convertKotlinSettings() = KotlinSettings().apply {
    // TODO Report wrong types.
    languageVersion(tryGetScalarNode("languageVersion"))
    apiVersion(tryGetScalarNode("apiVersion"))

    allWarningsAsErrors(tryGetScalarNode("allWarningsAsErrors")?.value?.toBoolean())
    suppressWarnings(tryGetScalarNode("suppressWarnings")?.value?.toBoolean())
    verbose(tryGetScalarNode("verbose")?.value?.toBoolean())
    debug(tryGetScalarNode("debug")?.value?.toBoolean())
    progressiveMode(tryGetScalarNode("progressiveMode")?.value?.toBoolean())

    freeCompilerArgs(tryGetScalarSequenceNode("freeCompilerArgs")?.values())
    linkerOpts(tryGetScalarSequenceNode("linkerOpts")?.values())
    languageFeatures(tryGetScalarSequenceNode("languageFeatures")?.values())
    optIns(tryGetScalarSequenceNode("optIns")?.values())

    serialization(tryGetChildNode("serialization")?.convertSerializationSettings())
}

context(ProblemReporterContext)
internal fun Node.convertSerializationSettings() = when (this) {
    is ScalarNode -> SerializationSettings().apply { engine(value) }
    is MappingNode -> SerializationSettings().apply { engine(tryGetScalarNode("engine")) }
    else -> null
}

context(ProblemReporterContext)
internal fun Node.convertComposeSettings() = when (this) {
    // TODO Report wrong value.
    is ScalarNode -> ComposeSettings().apply { enabled(value == "enabled") }
    is MappingNode -> ComposeSettings().apply { enabled(tryGetScalarNode("enabled")?.value?.toBoolean()) }
    else -> null
}