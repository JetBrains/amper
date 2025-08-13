/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import uk.org.webcompere.systemstubs.properties.SystemProperties
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalM2RepositoryFinderTest {
    @TempDir
    lateinit var mavenRepository: Path

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun mavenRepositoryPathDefault(systemProperties: SystemProperties, environmentVariables: EnvironmentVariables) {
        clearLocalM2MachineOverrides(systemProperties, environmentVariables)

        val repo = LocalM2RepositoryFinder.findPath()
        assertEquals(repo, Path(System.getProperty("user.home")).resolve(".m2/repository"))
    }

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun mavenRepositoryPathFromSystemPropertyTest(systemProperties: SystemProperties, environmentVariables: EnvironmentVariables) {
        clearLocalM2MachineOverrides(systemProperties, environmentVariables)

        try {
            systemProperties.set("maven.repo.local", mavenRepository / "maven")
            val repo = LocalM2RepositoryFinder.findPath()
            assertEquals(repo, mavenRepository / "maven")
        } finally {
            System.clearProperty("maven.repo.local")
        }
    }

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun mavenRepositoryPathFromUserHomeM2SettingsTest(systemProperties: SystemProperties, environmentVariables: EnvironmentVariables) {
        clearLocalM2MachineOverrides(systemProperties, environmentVariables)

        val m2SettingsPath = mavenRepository / ".m2"
        m2SettingsPath.createDirectories()
        Path("testData/metadata/xml/settings/settings.xml").copyTo(m2SettingsPath.resolve("settings.xml"))
        systemProperties.set("user.home", mavenRepository.absolutePathString())

        val repo = LocalM2RepositoryFinder.findPath()
        assertEquals(repo, Path("temp/rrr"))
    }

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun mavenRepositoryPathFromM2HomeSettingsTest(systemProperties: SystemProperties, environmentVariables: EnvironmentVariables) {
        clearLocalM2MachineOverrides(systemProperties, environmentVariables)

        val m2HomeOverridden = mavenRepository
        val m2SettingsPath = m2HomeOverridden / "conf"
        m2SettingsPath.createDirectories()
        Path("testData/metadata/xml/settings/settings.xml").copyTo(m2SettingsPath.resolve("settings.xml"))
        environmentVariables.set("M2_HOME", m2HomeOverridden.absolutePathString())

        val repo = LocalM2RepositoryFinder.findPath()
        assertEquals(repo, Path("temp/rrr"))
    }

    /**
     * Temporarily resets custom maven settings of the local machine to control the test configuration completely.
     */
    private fun clearLocalM2MachineOverrides(systemProperties: SystemProperties, environmentVariables: EnvironmentVariables) {
        systemProperties.set("maven.repo.local", "")
        systemProperties.set("user.home", Path("nothing-to-see-here").absolutePathString())
        environmentVariables.set("M2_HOME", "")
    }
}