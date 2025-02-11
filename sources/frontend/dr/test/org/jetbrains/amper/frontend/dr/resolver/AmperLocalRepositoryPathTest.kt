/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import uk.org.webcompere.systemstubs.properties.SystemProperties
import java.io.File
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

class AmperLocalRepositoryPathTest {

    @field:TempDir
    lateinit var temp: File

    private fun cacheRoot(): Path = temp.toPath()
        .resolve(UUID.randomUUID().toString().padEnd(8, '0').substring(1..8))

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `check amper local storage is resolved from system settings`(
        systemProperties: SystemProperties, environmentVariables: EnvironmentVariables
    ) {
        clearLocalAmperCacheOverrides(systemProperties, environmentVariables)
        val cacheRoot = cacheRoot()
        systemProperties.set("amper.cache.root", "$cacheRoot")
        val repo = AmperUserCacheRoot.fromCurrentUser()
        kotlin.test.assertEquals(repo.path, cacheRoot)
    }

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `check amper local storage is resolved from env variable`(
        systemProperties: SystemProperties, environmentVariables: EnvironmentVariables
    ) {
        clearLocalAmperCacheOverrides(systemProperties, environmentVariables)
        val cacheRoot = cacheRoot()
        environmentVariables.set("AMPER_CACHE_ROOT", "$cacheRoot")
        val repo = AmperUserCacheRoot.fromCurrentUser()
        kotlin.test.assertEquals(repo.path, cacheRoot)
    }

    /**
     * Temporarily resets custom amper settings of the local machine to control the test configuration completely.
     */
    private fun clearLocalAmperCacheOverrides(systemProperties: SystemProperties, environmentVariables: EnvironmentVariables) {
        systemProperties.set("amper.repo.local", "")
        systemProperties.set("user.home", Path("nothing-to-see-here").absolutePathString())
        environmentVariables.set("AMPER_REPO_LOCAL", "")
    }
}