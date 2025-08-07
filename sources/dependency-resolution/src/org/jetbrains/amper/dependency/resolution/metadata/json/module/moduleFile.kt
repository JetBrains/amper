/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.metadata.json.module

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.jetbrains.amper.dependency.resolution.metadata.json.json

/**
 * [Gradle Module Metadata specification](https://github.com/gradle/gradle/blob/master/platforms/documentation/docs/src/docs/design/gradle-module-metadata-latest-specification.md)
 */
internal fun String.parseMetadata(): Module = json.decodeFromString(this)
internal fun Module.serialize(): String = json.encodeToString(this)

@Serializable
internal data class Module(
    val formatVersion: String,
    val component: Component,
    val createdBy: CreatedBy? = null,
    val variants: List<Variant> = listOf(),
)

@Serializable
internal data class Component(
    val url: String? = null,
    val group: String,
    val module: String,
    val version: String,
    val attributes: Map<String, String> = mapOf(),
)

@Serializable
internal data class CreatedBy(
    val gradle: Gradle? = null,
    val maven: Maven? = null,
)

@Serializable
internal data class Gradle(
    val version: String,
    val buildId: String? = null,
)

@Serializable
internal data class Maven(
    val version: String,
    val buildId: String? = null,
)

@Serializable
internal data class Variant(
    val name: String,
    val attributes: Map<String, String> = mapOf(),
    val dependencies: List<Dependency> = listOf(),
    val dependencyConstraints: List<Dependency> = listOf(),
    val files: List<File> = listOf(),
    val `available-at`: AvailableAt? = null,
    val capabilities: List<Capability> = listOf(),
)

@Serializable
internal data class Dependency(
    val group: String,
    val module: String,
    val version: Version? = null,
    val attributes: Map<String, String> = mapOf(),
    val endorseStrictVersions: Boolean? = null,
    val reason: String? = null,
)

@Serializable
internal data class AvailableAt(
    val url: String,
    val group: String,
    val module: String,
    val version: String
)

@Serializable
internal data class Capability(
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
internal data class File(
    val name: String,
    val url: String,
    val size: Long? = null,
    val sha512: String? = null,
    val sha256: String? = null,
    val sha1: String? = null,
    val md5: String? = null,
)
