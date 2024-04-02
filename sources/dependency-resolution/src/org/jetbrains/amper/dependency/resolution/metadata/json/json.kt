/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("PropertyName")

package org.jetbrains.amper.dependency.resolution.metadata.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * [Gradle Module Metadata specification](https://github.com/gradle/gradle/blob/master/platforms/documentation/docs/src/docs/design/gradle-module-metadata-latest-specification.md)
 */
internal fun String.parseMetadata(): Module = json.decodeFromString(this)

internal fun Module.serialize(): String = json.encodeToString(this)

@Serializable
data class Module(
    val formatVersion: String,
    val component: Component,
    val createdBy: CreatedBy,
    val variants: List<Variant> = listOf(),
)

@Serializable
data class Component(
    val url: String? = null,
    val group: String,
    val module: String,
    val version: String,
    val attributes: Map<String, String> = mapOf(),
)

@Serializable
data class CreatedBy(
    val gradle: Gradle? = null,
    val maven: Maven? = null,
)

@Serializable
data class Gradle(
    val version: String,
    val buildId: String? = null,
)

@Serializable
data class Maven(
    val version: String,
    val buildId: String? = null,
)

@Serializable
data class Variant(
    val name: String,
    val attributes: Map<String, String> = mapOf(),
    val dependencies: List<Dependency> = listOf(),
    val dependencyConstraints: List<Dependency> = listOf(),
    val files: List<File> = listOf(),
    val `available-at`: AvailableAt? = null,
    val capabilities: List<Capability> = listOf(),
)

@Serializable
data class Dependency(
    val group: String,
    val module: String,
    val version: Version,
    val attributes: Map<String, String> = mapOf(),
    val endorseStrictVersions: Boolean? = null
)

@Serializable
data class AvailableAt(
    val url: String,
    val group: String,
    val module: String,
    val version: String
)

@Serializable
data class Capability(
    val group: String,
    val name: String,
    val version: String,
)

@Serializable
data class Version(
    val strictly: String? = null,
    val requires: String? = null,
    val prefers: String? = null,
)

@Serializable
data class File(
    val name: String,
    val url: String,
    val size: Long? = null,
    val sha512: String? = null,
    val sha256: String? = null,
    val sha1: String? = null,
    val md5: String? = null,
)
