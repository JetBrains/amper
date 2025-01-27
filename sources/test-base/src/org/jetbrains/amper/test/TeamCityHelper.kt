/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.jetbrains.amper.core.properties.readProperties
import java.nio.file.Path
import kotlin.io.path.*

@Suppress("unused")
object TeamCityHelper {
    val isUnderTeamCity: Boolean = System.getenv("TEAMCITY_VERSION") != null

    private fun requireRunUnderTeamcity() {
        require(isUnderTeamCity) {
            "This method must be run under TeamCity. Use 'isUnderTeamCity'."
        }
    }

    val checkoutDirectory: Path
        get() {
            requireRunUnderTeamcity()

            val name = "teamcity.build.checkoutDir"
            val value = systemProperties[name]
            if (value.isNullOrEmpty()) {
                error("TeamCity system property " + name + "was not found while running under TeamCity")
            }

            val file = Path(value)
            if (!file.isDirectory()) {
                error("TeamCity system property $name contains non existent directory: $file")
            }

            return file
        }

    val tempDirectory: Path
        get() {
            requireRunUnderTeamcity()
            val propertyName = "teamcity.build.tempDir"
            val tempPath = systemProperties[propertyName]
                ?: error("TeamCity must provide system property $propertyName")
            return Path(tempPath)
        }

    /**
     * A temp directory under [tempDirectory] that is guaranteed not to be reused between builds, and thus clean on
     * every CI build.
     *
     * It turns out that [tempDirectory] is sometimes non-empty (e.g., locked by some process).
     * This [cleanTempDirectory] contains the [buildId] to ensure it's clean.
     */
    val cleanTempDirectory: Path
        get() = (tempDirectory / buildId).createDirectories()

    /**
     * A directory that persists across TeamCity builds. Use with caution, as it contains files from previous builds.
     */
    val persistentCacheDirectory: Path
        get() {
            requireRunUnderTeamcity()
            val persistentCachePath = systemProperties["agent.persistent.cache"]
            check(!persistentCachePath.isNullOrBlank()) {
                "'agent.persistent.cache' system property is required under TeamCity"
            }
            return Path(persistentCachePath).createDirectories()
        }

    val systemProperties: Map<String, String> by lazy {
        requireRunUnderTeamcity()

        val systemPropertiesEnvName = "TEAMCITY_BUILD_PROPERTIES_FILE"
        val systemPropertiesFile = System.getenv(systemPropertiesEnvName)
        if (systemPropertiesFile == null || systemPropertiesFile.isEmpty()) {
            error("TeamCity environment variable $systemPropertiesEnvName was not found while running under TeamCity")
        }
        val file = Path(systemPropertiesFile)
        if (!file.exists()) {
            error("TeamCity system properties file is not found: $file")
        }
        loadPropertiesFile(file)
    }

    /**
     * Global build counter on TC server across the entire server.
     */
    val buildId: String
        get() = allProperties["teamcity.build.id"]
            ?: error("'teamcity.build.id' is missing in TeamCity build parameters, this should not happen")

    val allProperties: Map<String, String> by lazy {
        requireRunUnderTeamcity()

        val propertyName = "teamcity.configuration.properties.file"
        val value = systemProperties[propertyName]
        if (value.isNullOrEmpty()) {
            error("TeamCity system property '$propertyName is not found")
        }
        val file = Path(value)
        if (!file.exists()) {
            error("TeamCity configuration properties file was not found: $file")
        }
        loadPropertiesFile(file)
    }

    private fun loadPropertiesFile(file: Path): Map<String, String> {
        return file.readProperties()
            .map { (k, v) -> k as String to v as String }
            .toMap()
    }
}
