/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.api.*

class Settings : SchemaNode() {
    val java = nullableValue<JavaSettings>().default(JavaSettings())
    val jvm = value<JvmSettings>().default(JvmSettings())
    val kotlin = value<KotlinSettings>().default(KotlinSettings())
    val android = nullableValue<AndroidSettings>().default(AndroidSettings())
    val compose = nullableValue<ComposeSettings>()
    val junit = nullableValue<String>() // TODO Replace with enum.
    val ios = nullableValue<IosSettings>().default(IosSettings())
    val publishing = nullableValue<PublishingSettings>()
    val kover = nullableValue<KoverSettings>()
}

class JavaSettings : SchemaNode() {
    // TODO Replace with enum
    val source = nullableValue<String>()
}

class JvmSettings : SchemaNode() {
    // TODO Replace with enum
    val target = value<String>()
    val mainClass = value<String>()
}

class AndroidSettings : SchemaNode() {
    val compileSdk = value<String>()
    val minSdk = value<String>()
    val maxSdk = value<String>()
    val targetSdk = value<String>()
    val applicationId = value<String>()
    val namespace = value<String>()
}

class KotlinSettings : SchemaNode() {
    val serialization = nullableValue<SerializationSettings>()

    // TODO Replace with enum
    val languageVersion = value<String>()
    // TODO Replace with enum
    val apiVersion = value<String>()
    val allWarningsAsErrors = value<Boolean>()
    val freeCompilerArgs = value<List<String>>()
    val suppressWarnings = value<Boolean>()
    val verbose = value<Boolean>()
    val linkerOpts = value<List<String>>()
    val debug = value<Boolean>()
    val progressiveMode = value<Boolean>()
    // TODO Replace with enum
    val languageFeatures = value<List<String>>()
    // TODO Replace with enum
    val optIns = value<List<String>>()
}

class ComposeSettings : SchemaNode() {
    val enabled = value<Boolean>()
}

class SerializationSettings : SchemaNode() {
    val engine = value<String>()
}

class IosSettings : SchemaNode() {
    val teamId = nullableValue<String>()
    val framework = nullableValue<IosFrameworkSettings>().default(IosFrameworkSettings())
}

class IosFrameworkSettings : SchemaNode() {
    val basename = nullableValue<String>()
    val mappings = value<Map<String, String>>()
}

class PublishingSettings : SchemaNode() {
    val group = nullableValue<String>()
    val version = nullableValue<String>()
}

class KoverSettings : SchemaNode() {
    val enabled = nullableValue<Boolean>().default(false)
    val xml = nullableValue<KoverXmlSettings>()
    val html = nullableValue<KoverHtmlSettings>()
}

class KoverXmlSettings : SchemaNode() {
    val onCheck = nullableValue<Boolean>()
    val reportFile = nullableValue<String>()
}

class KoverHtmlSettings : SchemaNode() {
    val title = nullableValue<String>()
    val charset = nullableValue<String>()
    val onCheck = nullableValue<Boolean>()
    val reportDir = nullableValue<String>()
}