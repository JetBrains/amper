/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.resolver

import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.fileSize
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertEquals

class MavenResolverTest {
    @field:TempDir
    lateinit var tempDir: File

    @Test
    fun simpleResolve() {
        val root = tempDir.toPath()
        val resolver = MavenResolver(AmperUserCacheRoot(root))

        val result = resolver.resolve(coordinates = listOf("org.tinylog:slf4j-tinylog:2.7.0-M1"), listOf("https://repo1.maven.org/maven2"))
        val relative = result.map { it.relativeTo(root).toString().replace('\\', '/') }.sorted()
        assertEquals(
            listOf(
                ".m2.cache/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar",
                ".m2.cache/org/tinylog/slf4j-tinylog/2.7.0-M1/slf4j-tinylog-2.7.0-M1.jar",
                ".m2.cache/org/tinylog/tinylog-api/2.7.0-M1/tinylog-api-2.7.0-M1.jar",
            ),
            relative,
        )

        val slf4jApiJar = root.resolve(".m2.cache/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar")
        assertEquals(64579, slf4jApiJar.fileSize())
    }

    @Test
    fun ignoresProvidedDependencies() {
        val resolver = MavenResolver(AmperUserCacheRoot(tempDir.toPath()))

        // https://search.maven.org/artifact/org.tinylog/tinylog-api/2.7.0-M1/bundle
        val result = resolver.resolve(coordinates = listOf("org.tinylog:tinylog-api:2.7.0-M1"), listOf("https://repo1.maven.org/maven2"))
        val relative = result.map { it.relativeTo(tempDir.toPath()).toString().replace('\\', '/') }.sorted()
        assertEquals(
            listOf(".m2.cache/org/tinylog/tinylog-api/2.7.0-M1/tinylog-api-2.7.0-M1.jar"),
            relative,
        )
    }

    @Test
    fun negativeResolveSingleCoordinates() {
        val resolver = MavenResolver(AmperUserCacheRoot(tempDir.toPath()))

        val t = assertThrows<MavenResolverException> {
            resolver.resolve(coordinates = listOf("org.tinylog:slf4j-tinylog:9999"), listOf("https://repo1.maven.org/maven2"))
        }
        assertEquals(
            "Either metadata or pom required for org.tinylog:slf4j-tinylog:9999 ([https://repo1.maven.org/maven2])",
            t.message
        )
    }

    @Test
    fun negativeResolveMultipleCoordinates() {
        val resolver = MavenResolver(AmperUserCacheRoot(tempDir.toPath()))

        val t = assertThrows<MavenResolverException> {
            resolver.resolve(coordinates = listOf("org.tinylog:slf4j-tinylog:9999", "org.tinylog:xxx:9998"), listOf("https://repo1.maven.org/maven2"))
        }
        assertEquals(
            "Either metadata or pom required for org.tinylog:slf4j-tinylog:9999 ([https://repo1.maven.org/maven2])",
            t.message
        )
        assertEquals(
            "Either metadata or pom required for org.tinylog:xxx:9998 ([https://repo1.maven.org/maven2])",
            t.suppressed.single().message
        )
        return
    }
}
