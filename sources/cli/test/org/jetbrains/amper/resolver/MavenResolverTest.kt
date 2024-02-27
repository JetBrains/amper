/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.resolver

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.fileSize
import kotlin.io.path.relativeTo
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class MavenResolverTest {
    @field:TempDir
    lateinit var tempDir: File

    @Test
    fun simpleResolve() {
        val root = tempDir.toPath()
        val resolver = MavenResolver(AmperUserCacheRoot(root))

        val result = runBlocking {
            resolver.resolve(
                coordinates = listOf("org.tinylog:slf4j-tinylog:2.7.0-M1"),
                listOf("https://repo1.maven.org/maven2")
            )
        }
        val relative = result.map { it.relativeTo(root).joinToString("/") }.sorted()
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
        val result = runBlocking {
            resolver.resolve(
                coordinates = listOf("org.tinylog:tinylog-api:2.7.0-M1"),
                listOf("https://repo1.maven.org/maven2")
            )
        }
        val relative = result.map { it.relativeTo(tempDir.toPath()).joinToString("/") }.sorted()
        assertEquals(
            listOf(".m2.cache/org/tinylog/tinylog-api/2.7.0-M1/tinylog-api-2.7.0-M1.jar"),
            relative,
        )
    }

    @Test
    fun nativeTarget() {
        val resolver = MavenResolver(AmperUserCacheRoot(tempDir.toPath()))

        val result = runBlocking {
            resolver.resolve(
                coordinates = listOf("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0"),
                repositories = listOf("https://repo1.maven.org/maven2"),
                scope = ResolutionScope.COMPILE,
                platform = "native",
                nativeTarget = "mingw_x64",
            )
        }
        val relative = result.map { it.relativeTo(tempDir.toPath()).joinToString("/") }.sorted().joinToString("\n")
        assertEquals(
            """
                .m2.cache/org/jetbrains/kotlinx/kotlinx-datetime-mingwx64/0.5.0/kotlinx-datetime-mingwx64-0.5.0-cinterop-date.klib
                .m2.cache/org/jetbrains/kotlinx/kotlinx-datetime-mingwx64/0.5.0/kotlinx-datetime-mingwx64-0.5.0.klib
                .m2.cache/org/jetbrains/kotlinx/kotlinx-serialization-core-mingwx64/1.6.2/kotlinx-serialization-core-mingwx64-1.6.2.klib
            """.trimIndent(),
            relative,
        )
    }

    @Test
    fun respectsRuntimeScope() {
        val resolver = MavenResolver(AmperUserCacheRoot(tempDir.toPath()))

        // TODO find a smaller example of maven central artifact with runtime-scoped dependencies
        val result = runBlocking {
            resolver.resolve(
                coordinates = listOf("org.jetbrains.kotlin:kotlin-build-tools-impl:1.9.22"),
                repositories = listOf("https://repo1.maven.org/maven2"),
                scope = ResolutionScope.RUNTIME,
            )
        }
        val relative = result.map { it.relativeTo(tempDir.toPath()).joinToString("/") }.sorted()
        assertEquals(
            listOf(
                ".m2.cache/org/jetbrains/annotations/13.0/annotations-13.0.jar",
                ".m2.cache/org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-build-common/1.9.22/kotlin-build-common-1.9.22.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-build-tools-api/1.9.22/kotlin-build-tools-api-1.9.22.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-build-tools-impl/1.9.22/kotlin-build-tools-impl-1.9.22.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-compiler-embeddable/1.9.22/kotlin-compiler-embeddable-1.9.22.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-compiler-runner/1.9.22/kotlin-compiler-runner-1.9.22.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-daemon-client/1.9.22/kotlin-daemon-client-1.9.22.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-daemon-embeddable/1.9.22/kotlin-daemon-embeddable-1.9.22.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-reflect/1.6.10/kotlin-reflect-1.6.10.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-script-runtime/1.9.22/kotlin-script-runtime-1.9.22.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-stdlib-common/1.5.0/kotlin-stdlib-common-1.5.0.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-stdlib-jdk7/1.5.0/kotlin-stdlib-jdk7-1.5.0.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-stdlib-jdk8/1.5.0/kotlin-stdlib-jdk8-1.5.0.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-stdlib/1.9.22/kotlin-stdlib-1.9.22.jar",
                ".m2.cache/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.5.0/kotlinx-coroutines-core-jvm-1.5.0.jar",
            ),
            relative,
        )
    }

    @Test
    fun negativeResolveSingleCoordinates() {
        val resolver = MavenResolver(AmperUserCacheRoot(tempDir.toPath()))

        val t = assertThrows<MavenResolverException> {
            runBlocking {
                resolver.resolve(
                    coordinates = listOf("org.tinylog:slf4j-tinylog:9999"),
                    listOf("https://repo1.maven.org/maven2")
                )
            }
        }
        assertEquals(
            "Pom required for org.tinylog:slf4j-tinylog:9999 ([https://repo1.maven.org/maven2])",
            t.message
        )
    }

    @Test
    @Ignore
    // AMPER-395 DR: Unable to resolve gradle-tooling-api
    fun unableToResolveGradleToolingApiInCompileScope() {
        val resolver = MavenResolver(AmperUserCacheRoot(tempDir.toPath()))

        // TODO find a smaller example of maven central artifact with runtime-scoped dependencies
        val result = runBlocking {
            resolver.resolve(
                coordinates = listOf("org.gradle:gradle-tooling-api:8.4"),
                repositories = listOf(
                    "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2",
                    "https://repo.gradle.org/gradle/libs-releases",
                ),
                scope = ResolutionScope.COMPILE,
            )
        }
        val relative = result.map { it.relativeTo(tempDir.toPath()).joinToString("/") }.sorted()
        assertEquals(
            """
                whatever
            """.trimIndent(),
            relative.joinToString("\n")
        )
    }

    @Test
    fun negativeResolveMultipleCoordinates() {
        val resolver = MavenResolver(AmperUserCacheRoot(tempDir.toPath()))

        val t = assertThrows<MavenResolverException> {
            runBlocking {
                resolver.resolve(
                    coordinates = listOf("org.tinylog:slf4j-tinylog:9999", "org.tinylog:xxx:9998"),
                    listOf("https://repo1.maven.org/maven2")
                )
            }
        }
        assertEquals(
            "Pom required for org.tinylog:slf4j-tinylog:9999 ([https://repo1.maven.org/maven2])",
            t.message
        )
        assertEquals(
            "Pom required for org.tinylog:xxx:9998 ([https://repo1.maven.org/maven2])",
            t.suppressed.single().message
        )
        return
    }
}
