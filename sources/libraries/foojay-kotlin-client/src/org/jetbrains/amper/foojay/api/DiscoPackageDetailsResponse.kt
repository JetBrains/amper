/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.foojay.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.amper.foojay.serialization.EmptyStringAsNullSerializer

@Serializable
internal data class DiscoPackageDetailsResponse(
    val result: List<DiscoPackageDetails> = emptyList(),
    val message: String? = null,
)

@Serializable
data class DiscoPackageDetails(
    val filename: String,

    @Serializable(with = EmptyStringAsNullSerializer::class)
    @SerialName("direct_download_uri")
    val directDownloadUri: String?,

    @Serializable(with = EmptyStringAsNullSerializer::class)
    val checksum: String? = null,

    @Serializable(with = EmptyStringAsNullSerializer::class)
    @SerialName("checksum_type")
    val checksumType: String? = null,

    @Serializable(with = EmptyStringAsNullSerializer::class)
    @SerialName("checksum_uri")
    val checksumUri: String? = null,

    @Serializable(with = EmptyStringAsNullSerializer::class)
    @SerialName("signature_uri")
    val signatureUri: String? = null,

    @Serializable(with = EmptyStringAsNullSerializer::class)
    @SerialName("download_site_uri")
    val downloadSiteUri: String? = null,
)
