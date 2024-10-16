/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import java.nio.file.Path
import java.util.*
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
        return file.bufferedReader().use { val properties = Properties(); properties.load(it); properties }
            .map { (k, v) -> k as String to v as String }
            .toMap()
    }
}
