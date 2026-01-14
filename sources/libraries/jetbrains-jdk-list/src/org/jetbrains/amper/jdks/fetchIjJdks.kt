/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdks

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.tukaani.xz.SingleXZInputStream
import java.io.InputStream
import java.net.URI

private const val JetBrainsJdksJsonUrl = "https://download.jetbrains.com/jdk/feed/v1/jdks.json.xz"

private val json = Json {
    ignoreUnknownKeys = true
    useAlternativeNames = false // improves perf when not using @JsonNames (especially with ignoreUnknownKeys)
}

@OptIn(ExperimentalSerializationApi::class)
fun fetchIjJdks(): List<IjJdkFamily> = URI(JetBrainsJdksJsonUrl).toURL()
    .openStream()
    .xzUncompressed()
    .use { json.decodeFromStream<IjJdksJsonRoot>(it).jdks }

private fun InputStream.xzUncompressed() = SingleXZInputStream(this)

@Serializable
private data class IjJdksJsonRoot(
    val jdks: List<IjJdkFamily>,
)

@Serializable
data class IjJdkFamily(
    val vendor: String,
    val product: String,
    val preview: Boolean = false,
    @SerialName("jdk_version_major")
    val jdkVersionMajor: Int,
    @SerialName("jdk_version")
    val jdkVersion: String,
    val packages: List<IjJdkPackage>, // so far, it has always been a single package
)

@Serializable
data class IjJdkPackage(
    val os: IjJdkOs,
    val arch: IjJdkArchitecture,
    val version: String,
    val url: String,
    @SerialName("unpacked_size")
    val unpackedSize: Long,
    @SerialName("archive_size")
    val archiveSize: Long,
    val sha256: String,
)

enum class IjJdkOs {
    @SerialName("linux")
    Linux,
    @SerialName("macOS")
    MacOS,
    @SerialName("windows")
    Windows,
}

enum class IjJdkArchitecture {
    @SerialName("x86_64")
    x64,
    @SerialName("aarch64")
    aarch64
}
