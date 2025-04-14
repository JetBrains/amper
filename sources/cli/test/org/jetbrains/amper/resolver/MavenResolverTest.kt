/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.resolver

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.cli.UserReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.toRepositories
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

private const val MAVEN_CENTRAL_CACHE_REDIRECTOR = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2"

class MavenResolverTest {
    @TempDir
    lateinit var amperCacheRoot: Path

    @Test
    fun simpleResolve() {
        val resolver = MavenResolver(AmperUserCacheRoot(amperCacheRoot))

        val result = runBlocking {
            resolver.resolve(
                coordinates = listOf("org.tinylog:slf4j-tinylog:2.7.0-M1"),
                repositories = listOf(MAVEN_CENTRAL_CACHE_REDIRECTOR).toRepositories(),
                scope = ResolutionScope.COMPILE,
                platform = ResolutionPlatform.JVM,
                resolveSourceMoniker = "test",
            )
        }
        val relative = result.map { it.relativeTo(amperCacheRoot).joinToString("/") }.sorted()
        assertEquals(
            listOf(
                ".m2.cache/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar",
                ".m2.cache/org/tinylog/slf4j-tinylog/2.7.0-M1/slf4j-tinylog-2.7.0-M1.jar",
                ".m2.cache/org/tinylog/tinylog-api/2.7.0-M1/tinylog-api-2.7.0-M1.jar",
            ),
            relative,
        )

        val slf4jApiJar = amperCacheRoot.resolve(".m2.cache/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar")
        assertEquals(64579, slf4jApiJar.fileSize())
    }

    @Test
    fun ignoresProvidedDependencies() {
        val resolver = MavenResolver(AmperUserCacheRoot(amperCacheRoot))

        // https://search.maven.org/artifact/org.tinylog/tinylog-api/2.7.0-M1/bundle
        val result = runBlocking {
            resolver.resolve(
                coordinates = listOf("org.tinylog:tinylog-api:2.7.0-M1"),
                repositories = listOf(MAVEN_CENTRAL_CACHE_REDIRECTOR).toRepositories(),
                scope = ResolutionScope.COMPILE,
                platform = ResolutionPlatform.JVM,
                resolveSourceMoniker = "test",
            )
        }
        val relative = result.map { it.relativeTo(amperCacheRoot).joinToString("/") }.sorted()
        assertEquals(
            listOf(".m2.cache/org/tinylog/tinylog-api/2.7.0-M1/tinylog-api-2.7.0-M1.jar"),
            relative,
        )
    }

    @Test
    fun nativeTarget() {
        val resolver = MavenResolver(AmperUserCacheRoot(amperCacheRoot))

        val result = runBlocking {
            resolver.resolve(
                coordinates = listOf("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0"),
                repositories = listOf(MAVEN_CENTRAL_CACHE_REDIRECTOR).toRepositories(),
                scope = ResolutionScope.COMPILE,
                platform = ResolutionPlatform.MINGW_X64,
                resolveSourceMoniker = "test",
            )
        }
        val relative = result.map { it.relativeTo(amperCacheRoot).joinToString("/") }.sorted().joinToString("\n")
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
        val resolver = MavenResolver(AmperUserCacheRoot(amperCacheRoot))

        // TODO find a smaller example of maven central artifact with runtime-scoped dependencies
        val result = runBlocking {
            resolver.resolve(
                coordinates = listOf("org.jetbrains.kotlin:kotlin-build-tools-impl:1.9.22"),
                repositories = listOf(MAVEN_CENTRAL_CACHE_REDIRECTOR).toRepositories(),
                scope = ResolutionScope.RUNTIME,
                platform = ResolutionPlatform.JVM,
                resolveSourceMoniker = "test",
            )
        }
        val relative = result.map { it.relativeTo(amperCacheRoot).joinToString("/") }.sorted()
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
                ".m2.cache/org/jetbrains/kotlin/kotlin-stdlib-jdk7/1.8.0/kotlin-stdlib-jdk7-1.8.0.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-stdlib-jdk8/1.8.0/kotlin-stdlib-jdk8-1.8.0.jar",
                ".m2.cache/org/jetbrains/kotlin/kotlin-stdlib/1.9.22/kotlin-stdlib-1.9.22.jar",
                ".m2.cache/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.5.0/kotlinx-coroutines-core-jvm-1.5.0.jar",
            ),
            relative,
        )
    }

    @Test
    fun negativeResolveSingleCoordinates() {
        val resolver = MavenResolver(AmperUserCacheRoot(amperCacheRoot))

        val t = assertThrows<UserReadableError> {
            runBlocking {
                resolver.resolve(
                    coordinates = listOf("org.tinylog:slf4j-tinylog:9999"),
                    repositories = listOf(MAVEN_CENTRAL_CACHE_REDIRECTOR).toRepositories(),
                    scope = ResolutionScope.COMPILE,
                    platform = ResolutionPlatform.JVM,
                    resolveSourceMoniker = "test",
                )
            }
        }
        assertEquals(
            """
                Unable to resolve dependencies for test:

                Unable to resolve dependency org.tinylog:slf4j-tinylog:9999
                  Unable to download checksums of file slf4j-tinylog-9999.pom
                  Unable to download checksums of file slf4j-tinylog-9999.module
                Repositories used for resolution:
                  - $MAVEN_CENTRAL_CACHE_REDIRECTOR
            """.trimIndent(),
            t.message
        )
    }

    @Test
    fun negativeResolvePlatformSupportWasNotFound() = runTest(timeout = Duration.INFINITE ) {
        val resolver = MavenResolver(AmperUserCacheRoot(amperCacheRoot))

        // kotlinx-datetime:0.2.1 is available for macos_x64
        val macosX64 = resolver.resolve(
            coordinates = listOf("org.jetbrains.kotlinx:kotlinx-datetime:0.2.1"),
            repositories = listOf(MAVEN_CENTRAL_CACHE_REDIRECTOR).toRepositories(),
            scope = ResolutionScope.COMPILE,
            platform = ResolutionPlatform.MACOS_X64,
            resolveSourceMoniker = "test",
        )
        assertTrue(macosX64.any { it.name == "kotlinx-datetime-macosx64-0.2.1.klib" },
            message = "kotlinx-datetime-macosx64-0.2.1.klib must be found in resolve result: ${macosX64.toList()}")

        // kotlinx-datetime:0.2.1 is NOT available for macos_arm64
        val t = assertThrows<UserReadableError> {
            runBlocking {
                resolver.resolve(
                    coordinates = listOf("org.jetbrains.kotlinx:kotlinx-datetime:0.2.1"),
                    repositories = listOf(MAVEN_CENTRAL_CACHE_REDIRECTOR).toRepositories(),
                    scope = ResolutionScope.COMPILE,
                    platform = ResolutionPlatform.MACOS_ARM64,
                    resolveSourceMoniker = "test",
                )
            }
        }
        assertEquals(
            """
               Unable to resolve dependencies for test:

               No variant for the platform macosArm64 is provided by the library org.jetbrains.kotlinx:kotlinx-datetime:0.2.1
               """.trimIndent(),
            t.message
        )
    }

    @Test
    fun unableToResolveGradleToolingApiInCompileScope() {
        val resolver = MavenResolver(AmperUserCacheRoot(amperCacheRoot))

        // TODO find a smaller example of maven central artifact with runtime-scoped dependencies
        val result = runBlocking {
            resolver.resolve(
                coordinates = listOf("org.gradle:gradle-tooling-api:8.4"),
                repositories = listOf(
                    MAVEN_CENTRAL_CACHE_REDIRECTOR,
                    "https://repo.gradle.org/gradle/libs-releases", // TODO add to cache-redirector?
                ).toRepositories(),
                scope = ResolutionScope.COMPILE,
                platform = ResolutionPlatform.JVM,
                resolveSourceMoniker = "test",
            )
        }
        val relative = result.map { it.relativeTo(amperCacheRoot).joinToString("/") }.sorted()
        assertEquals(
            listOf(
                ".m2.cache/org/gradle/gradle-tooling-api/8.4/gradle-tooling-api-8.4.jar",
                ".m2.cache/org/slf4j/slf4j-api/1.7.30/slf4j-api-1.7.30.jar"
            ),
            relative
        )
    }

    @Test
    fun negativeResolveMultipleCoordinates() {
        val resolver = MavenResolver(AmperUserCacheRoot(amperCacheRoot))

        val t = assertThrows<UserReadableError> {
            runBlocking {
                resolver.resolve(
                    coordinates = listOf("org.tinylog:slf4j-tinylog:9999", "org.tinylog:xxx:9998"),
                    repositories = listOf(MAVEN_CENTRAL_CACHE_REDIRECTOR).toRepositories(),
                    scope = ResolutionScope.COMPILE,
                    platform = ResolutionPlatform.JVM,
                    resolveSourceMoniker = "test",
                )
            }
        }
        assertEquals(
            """
                Unable to resolve dependencies for test:

                Unable to resolve dependency org.tinylog:slf4j-tinylog:9999
                  Unable to download checksums of file slf4j-tinylog-9999.pom
                  Unable to download checksums of file slf4j-tinylog-9999.module
                Repositories used for resolution:
                  - $MAVEN_CENTRAL_CACHE_REDIRECTOR

                Unable to resolve dependency org.tinylog:xxx:9998
                  Unable to download checksums of file xxx-9998.pom
                  Unable to download checksums of file xxx-9998.module
                Repositories used for resolution:
                  - $MAVEN_CENTRAL_CACHE_REDIRECTOR
            """.trimIndent(),
            t.message
        )
    }
}
