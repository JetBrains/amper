/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.api.SchemaNode

class Settings : SchemaNode() {
    val java = nullableValue<JavaSettings>()
    val jvm = nullableValue<JvmSettings>()
    val kotlin = value<KotlinSettings>().default(KotlinSettings())
    val android = nullableValue<AndroidSettings>()
    val compose = nullableValue<ComposeSettings>()
    val junit = nullableValue<String>() // TODO Replace with enum.
    val ios = nullableValue<IosSettings>()
    val publishing = nullableValue<PublishingSettings>()
    val kover = nullableValue<KoverSettings>()
}

class JavaSettings : SchemaNode() {
    // TODO Replace with enum
    val source = nullableValue<String>()
}

class JvmSettings : SchemaNode() {
    // TODO Replace with enum
    val target = value<String>().default("17")
    val mainClass = nullableValue<String>()
}

class AndroidSettings : SchemaNode() {
    val compileSdk = nullableValue<String>()
    val minSdk = nullableValue<String>()
    val maxSdk = nullableValue<String>()
    val targetSdk = nullableValue<String>()
    val applicationId = nullableValue<String>()
    val namespace = nullableValue<String>()
}

class KotlinSettings : SchemaNode() {
    val serialization = nullableValue<SerializationSettings>()

    // TODO Replace with enum
    val languageVersion = value<String>().default("1.9")
    // TODO Replace with enum
    val apiVersion = nullableValue<String>()
    val allWarningsAsErrors = value<Boolean>().default(false)
    val freeCompilerArgs = nullableValue<List<String>>()
    val suppressWarnings = value<Boolean>().default(false)
    val verbose = value<Boolean>().default(false)
    val linkerOpts = nullableValue<List<String>>()
    val debug = value<Boolean>().default(false)
    val progressiveMode = value<Boolean>().default(false)
    // TODO Replace with enum
    val languageFeatures = nullableValue<List<String>>()
    // TODO Replace with enum
    val optIns = nullableValue<List<String>>()
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
    val isStatic = nullableValue<Boolean>().default(false)
    val mappings = nullableValue<Map<String, String>>()
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