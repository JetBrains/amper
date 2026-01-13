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
    val default: Boolean = false,
    val preview: Boolean = false,
    val flavour: String? = null,
    @SerialName("jdk_version_major")
    val jdkVersionMajor: Int,
    @SerialName("jdk_version")
    val jdkVersion: String,
    @SerialName("jdk_vendor_version")
    val jdkVendorVersion: String? = null,
    @SerialName("suggested_sdk_name")
    val suggestedSdkName: String,
    @SerialName("shared_index_aliases")
    val sharedIndexAliases: List<String>,
    val packages: List<IjJdkPackage>, // so far, it has always been a single package
)

@Serializable
data class IjJdkPackage(
    val os: IjJdkOs,
    val arch: IjJdkArchitecture,
    val version: String,
    val url: String,
    val package_type: String,
    val unpack_prefix_filter: String,
    val package_root_prefix: String,
    val package_to_java_home_prefix: String,
    val archive_file_name: String,
    val install_folder_name: String,
    val unpacked_size: Long,
    val archive_size: Long,
    val sha256: String,
    val filter: PackageFilter? = null,
)

@Serializable
data class PackageFilter(
    val type: String,
    val arch: String,
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
