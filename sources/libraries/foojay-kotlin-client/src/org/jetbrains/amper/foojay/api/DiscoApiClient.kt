/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.foojay.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.jetbrains.amper.foojay.model.Architecture
import org.jetbrains.amper.foojay.model.ArchiveType
import org.jetbrains.amper.foojay.model.Distribution
import org.jetbrains.amper.foojay.model.OperatingSystem
import org.jetbrains.amper.foojay.model.PackageType
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed interface RequestedVersion {
    data class LatestOfMajor(val majorJdkVersion: Int) : RequestedVersion
    data class Exact(val version: String) : RequestedVersion
}

/**
 * A Kotlin client for the [Foojay Disco API](https://github.com/foojayio/discoapi/), backed by a Ktor [HttpClient].
 *
 * Use the [configureClient] lambda to add extra configuration to the [HttpClient] used internally.
 */
class DiscoApiClient(configureClient: HttpClientConfig<*>.() -> Unit = {}) : AutoCloseable {

    private val httpClient: HttpClient = HttpClient {
        expectSuccess = true

        install(ContentEncoding) {
            deflate()
            gzip()
            identity()
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnException(maxRetries = 3, retryOnTimeout = true)
            exponentialDelay(baseDelayMs = 200)
        }

        // has to be after HttpRequestRetry because we use retryOnTimeout
        install(HttpTimeout) {
            connectTimeoutMillis = 10.seconds.inWholeMilliseconds
            requestTimeoutMillis = 2.minutes.inWholeMilliseconds // these are just API calls, they shouldn't take long
        }

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true // make sure we don't fail unnecessarily
            })
        }

        configureClient()
    }

    /**
     * Gets the list of packages matching the given criteria.
     *
     * @param version The version of the JDK to get.
     * @param packageType The type of the package to get (JDK / JRE). If null, both types are returned.
     * @param distributions The acceptable distributions for the returned packages.
     * @param architectures The acceptable architectures for the returned packages (null means don't filter).
     * @param archiveTypes The acceptable archive types for the returned packages (null means don't filter).
     * @param operatingSystems The acceptable operating systems for the returned packages (null means don't filter).
     * @param javafxBundled Whether the returned packages should have JavaFX bundled (null means that we don't care).
     * @param directlyDownloadable Whether the returned packages should be directly downloadable (null means that we don't care).
     * @param freeToUseInProduction Whether the returned packages should be free to use in production (null means that we don't care).
     */
    suspend fun listPackages(
        version: RequestedVersion,
        packageType: PackageType? = null, // this filter only supports one value, don't make it a list!
        // we don't support null because we only want to get distributions that appear in our enum
        distributions: List<Distribution> = Distribution.entries,
        architectures: List<Architecture>? = null,
        archiveTypes: List<ArchiveType>? = null,
        operatingSystems: List<OperatingSystem>? = null,
        javafxBundled: Boolean? = false,
        directlyDownloadable: Boolean? = true,
        freeToUseInProduction: Boolean? = true,
    ): List<DiscoPackage> {
        // Example: https://api.foojay.io/disco/v3.0/packages?version=23&distribution=zulu,temurin&architecture=x64&archive_type=zip,tar.gz&package_type=jdk&javafx_bundled=false&directly_downloadable=true&latest=overall&free_to_use_in_production=true&operating_system=macos
        val response = httpClient.get("https://api.foojay.io/disco/v3.0/packages") {
            url {
                when (version) {
                    is RequestedVersion.LatestOfMajor -> {
                        parameters.append("jdk_version", version.majorJdkVersion.toString())

                        // 'available' is the choice that always yields what we want: the latest available build for the given
                        // major version (or each major version if unspecified) for each of the given distributions (or for each
                        // known distribution, if unspecified).
                        parameters.append("latest", "available")
                    }
                    is RequestedVersion.Exact -> {
                        parameters.append("version", version.version)
                    }
                }

                packageType?.let { packageType ->
                    parameters.append("package_type", packageType.apiValue)
                }
                distributions.forEach { distribution ->
                    parameters.append("distribution", distribution.apiValue)
                }
                architectures?.forEach { architecture ->
                    parameters.append("architecture", architecture.apiValue)
                }
                archiveTypes?.forEach { archiveType ->
                    parameters.append("archive_type", archiveType.apiValue)
                }
                operatingSystems?.forEach { operatingSystem ->
                    parameters.append("operating_system", operatingSystem.apiValue)
                    parameters.append("libc_type", operatingSystem.libCType.apiValue)
                }
                javafxBundled?.let {
                    parameters.append("javafx_bundled", it.toString())
                }
                freeToUseInProduction?.let {
                    parameters.append("free_to_use_in_production", it.toString())
                }
                directlyDownloadable?.let {
                    parameters.append("directly_downloadable", it.toString())
                }
            }
        }
        return response.body<DiscoPackagesResponse>().result
    }

    suspend fun getPackageDetails(packageId: PackageId): DiscoPackageDetails? =
        httpClient.get("https://api.foojay.io/disco/v3.0/ids/${packageId.value}")
            .body<DiscoPackageDetailsResponse>()
            .result
            .firstOrNull()

    override fun close() {
        httpClient.close()
    }
}
