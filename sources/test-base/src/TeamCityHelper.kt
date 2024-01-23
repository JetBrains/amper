/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import java.nio.file.Files
import java.nio.file.Path
import java.util.*

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
                throw RuntimeException("TeamCity system property " + name + "was not found while running under TeamCity")
            }

            val file = Path.of(value)
            if (!Files.isDirectory(file)) {
                throw RuntimeException("TeamCity system property $name contains non existent directory: $file")
            }

            return file
        }

    val tempDirectory: Path
        get() {
            requireRunUnderTeamcity()
            val propertyName = "teamcity.build.tempDir"
            val tempPath = systemProperties[propertyName]
                ?: throw IllegalStateException("TeamCity must provide system property $propertyName")
            return Path.of(tempPath)
        }

    val systemProperties: Map<String, String> by lazy {
        requireRunUnderTeamcity()

        val systemPropertiesEnvName = "TEAMCITY_BUILD_PROPERTIES_FILE"
        val systemPropertiesFile = System.getenv(systemPropertiesEnvName)
        if (systemPropertiesFile == null || systemPropertiesFile.isEmpty()) {
            throw RuntimeException("TeamCity environment variable $systemPropertiesEnvName was not found while running under TeamCity")
        }
        val file = Path.of(systemPropertiesFile)
        if (!Files.exists(file)) {
            throw RuntimeException("TeamCity system properties file is not found: $file")
        }
        loadPropertiesFile(file)
    }

    val allProperties: Map<String, String> by lazy {
        requireRunUnderTeamcity()

        val propertyName = "teamcity.configuration.properties.file"
        val value = systemProperties[propertyName]
        if (value.isNullOrEmpty()) {
            throw RuntimeException("TeamCity system property '$propertyName is not found")
        }
        val file = Path.of(value)
        if (!Files.exists(file)) {
            throw RuntimeException("TeamCity configuration properties file was not found: $file")
        }
        loadPropertiesFile(file)
    }

    private fun loadPropertiesFile(file: Path): Map<String, String> {
        return Files.newBufferedReader(file).use { val properties = Properties(); properties.load(it); properties }
            .map { (k, v) -> k as String to v as String }
            .toMap()
    }
}
