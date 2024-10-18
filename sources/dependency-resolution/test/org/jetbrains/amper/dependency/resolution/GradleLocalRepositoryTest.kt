/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Variant
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.*
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradleLocalRepositoryTest {

    @field:TempDir
    lateinit var temp: File

    private val gradleLocalRepository: GradleLocalRepository
        get() = GradleLocalRepository(temp.toPath())

    @Test
    fun `get name without extension`() {
        assertEquals("kotlin-test-1.9.10", getNameWithoutExtension(kotlinTest()))
    }

    @Test
    fun `guess path`() {
        val node = kotlinTest()
        var path = runBlocking { gradleLocalRepository.guessPath(node, "${getNameWithoutExtension(node)}.jar") }
        assertNull(path)

        val sha1 = computeHash("sha1", randomString().toByteArray())
        assertTrue(
            File(
                temp,
                "org.jetbrains.kotlin/kotlin-test/1.9.10/$sha1/kotlin-test-1.9.10.jar"
            ).mkdirs()
        )
        path = runBlocking { gradleLocalRepository.guessPath(node, "${getNameWithoutExtension(node)}.jar") }
        assertEquals(
            "org.jetbrains.kotlin/kotlin-test/1.9.10/$sha1/kotlin-test-1.9.10.jar",
            path?.relativeTo(temp.toPath()).toString().replace('\\', '/')
        )
    }

    @Test
    fun `guess path with variant`() {
        val sha1 = computeHash("sha1", randomString().toByteArray())
        val node = kotlinTest().also {
            it.variants = listOf(
                Variant(
                    "",
                    files = listOf(
                        org.jetbrains.amper.dependency.resolution.metadata.json.module.File(
                            name = "kotlin-test-1.9.10.jar",
                            "",
                            0,
                            "",
                            "",
                            sha1 = sha1,
                            "",
                        )
                    ),
                )
            )
        }
        val path = runBlocking { gradleLocalRepository.guessPath(node, "${getNameWithoutExtension(node)}.jar") }
        assertEquals(
            "org.jetbrains.kotlin/kotlin-test/1.9.10/$sha1/kotlin-test-1.9.10.jar",
            path?.relativeTo(temp.toPath()).toString().replace('\\', '/')
        )
    }

    @Test
    fun `get path`() {
        val bytes = randomString().toByteArray()
        val sha1 = computeHash("sha1", bytes)
        val path = gradleLocalRepository.getPath(kotlinTest(), "${getNameWithoutExtension(kotlinTest())}.jar", sha1)
        assertEquals(
            "org.jetbrains.kotlin/kotlin-test/1.9.10/$sha1/kotlin-test-1.9.10.jar",
            path.relativeTo(temp.toPath()).toString().replace('\\', '/')
        )
    }

    private fun kotlinTest() = MavenDependency(
        SettingsBuilder {
            cache = {
                localRepository = gradleLocalRepository
            }
        }.settings
        ,
        "org.jetbrains.kotlin", "kotlin-test", "1.9.10"
    )

    private fun randomString() = UUID.randomUUID().toString()
}
