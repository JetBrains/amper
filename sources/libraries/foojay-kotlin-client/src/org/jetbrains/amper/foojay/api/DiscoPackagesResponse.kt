/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.foojay.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.amper.foojay.model.Architecture
import org.jetbrains.amper.foojay.model.ArchiveType
import org.jetbrains.amper.foojay.model.Distribution
import org.jetbrains.amper.foojay.model.LibCType
import org.jetbrains.amper.foojay.model.PackageType
import org.jetbrains.amper.foojay.serialization.EmptyStringAsNullSerializer
import org.jetbrains.amper.foojay.serialization.LenientBooleanSerializer

@Serializable
internal data class DiscoPackagesResponse(
    // null items are not expected, but it happened before: https://github.com/foojayio/discoapi/issues/132
    val result: List<DiscoPackage?> = emptyList(),
    val message: String? = null,
)

@JvmInline
@Serializable
value class PackageId(val value: String)

@Serializable
data class DiscoPackage(
    val id: PackageId,
    val distribution: Distribution,
    val size: Long,

    @SerialName("java_version")
    val javaVersion: String,

    @SerialName("major_version")
    val majorVersion: Int,

    @SerialName("distribution_version")
    val distributionVersion: String? = null,

    @SerialName("jdk_version")
    val jdkVersion: Int? = null,

    @SerialName("release_status")
    val releaseStatus: String? = null,

    @SerialName("term_of_support")
    val termOfSupport: String? = null,

    @SerialName("operating_system")
    val operatingSystem: String? = null,

    @SerialName("architecture")
    val architecture: Architecture? = null,

    @SerialName("lib_c_type")
    val libCType: LibCType? = null,

    @SerialName("archive_type")
    val archiveType: ArchiveType? = null,

    @SerialName("package_type")
    val packageType: PackageType,

    @SerialName("feature")
    val features: List<String> = emptyList(),

    @SerialName("javafx_bundled")
    @Serializable(with = LenientBooleanSerializer::class)
    val javafxBundled: Boolean? = null,

    @SerialName("directly_downloadable")
    @Serializable(with = LenientBooleanSerializer::class)
    val directlyDownloadable: Boolean? = null,

    @Serializable(with = LenientBooleanSerializer::class)
    @SerialName("latest_build_available")
    val latestBuildAvailable: Boolean? = null,

    @Serializable(with = LenientBooleanSerializer::class)
    @SerialName("free_use_in_production")
    val freeToUseInProduction: Boolean? = null,

    @Serializable(with = LenientBooleanSerializer::class)
    @SerialName("tck_tested")
    val tckTested: Boolean? = null,

    @Serializable(with = EmptyStringAsNullSerializer::class)
    @SerialName("tck_cert_uri")
    val tckCertUri: String? = null,

    @Serializable(with = LenientBooleanSerializer::class)
    @SerialName("aqavit_certified")
    val aqavitCertified: Boolean? = null,

    @Serializable(with = EmptyStringAsNullSerializer::class)
    @SerialName("aqavit_cert_uri")
    val aqavitCertUri: String? = null,

    @Serializable(with = EmptyStringAsNullSerializer::class)
    @SerialName("filename")
    val fileName: String? = null,

    @Serializable(with = EmptyStringAsNullSerializer::class)
    @SerialName("fpu")
    val floatingPointUnit: String? = null, // ARM-only

    val links: PackageLinks? = null,
)

@Serializable
data class PackageLinks(
    @SerialName("pkg_info_uri")
    val packageInfoUri: String? = null,
    @SerialName("pkg_download_redirect")
    val packageDownloadRedirect: String? = null,
)
