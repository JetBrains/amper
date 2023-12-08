/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.api.*

class Settings : SchemaNode() {
    val java = value(default = JavaSettings())
    val jvm = value(default = JvmSettings())
    val kotlin = value(default = KotlinSettings())
    val android = value(default = AndroidSettings())
    val compose = nullableValue<ComposeSettings>()
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