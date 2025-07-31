/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import uk.org.webcompere.systemstubs.properties.SystemProperties
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertEquals

class MavenLocalRepositoryTest {
    @TempDir
    lateinit var mavenRepository: Path

    private val mavenLocalRepository: MavenLocalRepository
        get() = MavenLocalRepository(mavenRepository)

    @Test
    fun `get name`() {
        assertEquals("kotlin-test-1.9.10.jar", "${getNameWithoutExtension(kotlinTest())}.jar")
    }

    @Test
    fun `guess path`() = runTest {
        val node = kotlinTest()
        val path = mavenLocalRepository.guessPath(node, "${getNameWithoutExtension(node)}.jar")
        assertEquals(
            "org/jetbrains/kotlin/kotlin-test/1.9.10/kotlin-test-1.9.10.jar",
            path.relativeTo(mavenRepository).toString().replace('\\', '/')
        )
    }

    @Test
    fun `get path`() {
        val sha1 = computeHash("sha1", randomString().toByteArray())
        val path = mavenLocalRepository.getPath(kotlinTest(), "${getNameWithoutExtension(kotlinTest())}.jar", sha1)
        assertEquals(
            "org/jetbrains/kotlin/kotlin-test/1.9.10/kotlin-test-1.9.10.jar",
            path.relativeTo(mavenRepository).toString().replace('\\', '/')
        )
    }

    private fun kotlinTest() = MavenDependencyImpl(
        SettingsBuilder {
            cache = {
                localRepository = mavenLocalRepository
            }
        }.settings,
        "org.jetbrains.kotlin", "kotlin-test", "1.9.10"
    )

    private fun randomString() = UUID.randomUUID().toString()
}
