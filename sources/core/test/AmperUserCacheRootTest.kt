/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import uk.org.webcompere.systemstubs.properties.SystemProperties
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.test.assertEquals

class AmperUserCacheRootTest {
    @TempDir
    lateinit var tempRoot: Path

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `check Amper user cache is resolved from system settings`(
        systemProperties: SystemProperties, environmentVariables: EnvironmentVariables
    ) {
        clearLocalAmperCacheOverrides(systemProperties, environmentVariables)
        systemProperties.set("amper.cache.root", tempRoot.pathString)
        val repo = AmperUserCacheRoot.fromCurrentUser()
        assertEquals(repo.path, tempRoot)
    }

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `check Amper user cache is resolved from env variable`(
        systemProperties: SystemProperties, environmentVariables: EnvironmentVariables
    ) {
        clearLocalAmperCacheOverrides(systemProperties, environmentVariables)
        environmentVariables.set("AMPER_CACHE_ROOT", tempRoot.pathString)
        val repo = AmperUserCacheRoot.fromCurrentUser()
        assertEquals(repo.path, tempRoot)
    }

    /**
     * Temporarily resets custom amper settings of the local machine to control the test configuration completely.
     */
    private fun clearLocalAmperCacheOverrides(
        systemProperties: SystemProperties,
        environmentVariables: EnvironmentVariables
    ) {
        systemProperties.remove("amper.cache.root")
        environmentVariables.remove("AMPER_CACHE_ROOT")
    }
}
