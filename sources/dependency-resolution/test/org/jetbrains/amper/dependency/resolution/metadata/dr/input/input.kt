/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("PropertyName")

package org.jetbrains.amper.dependency.resolution.metadata.dr.input

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**a
 * [Gradle Module Metadata specification](https://github.com/gradle/gradle/blob/master/platforms/documentation/docs/src/docs/design/gradle-module-metadata-latest-specification.md)
 */
internal fun String.parseMetadata(): DRInput = json.decodeFromString(this)

internal fun DRInput.serialize(): String = json.encodeToString(this)

@Serializable
data class DRInput(
    // ResolutionScope
    val scope: String,
    // PlatformType
    var platform: String,
    var nativeTarget: String? = null,
    var repositories: List<String> = listOf(),
    var downloadSources: Boolean? = null,
    var dependency: Dependency2,
)

@Serializable
data class Dependency2(
    val group: String,
    val module: String,
    val version: String,
//    val attributes: Map<String, String> = mapOf(),
    val endorseStrictVersions: Boolean? = null
)

//@Serializable
//data class Capability(
//    val group: String,
//    val name: String,
//    val version: String,
//)
// @Serializable
//data class Version(
//    val requires: String,
//)

