/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DependencyFileTest {

    @field:TempDir
    lateinit var temp: File

    private val amperPath: Path
        get() = temp.resolve(".amper").toPath()

    private val gradlePath: Path
        get() = temp.resolve(".gradle").toPath()

    @Test
    fun `kotlin-test-1_9_20 module hash`() {
        val path = gradlePath
        Context {
            cache = {
                amperCache = amperPath
                localRepositories = listOf(GradleLocalRepository(path))
            }
        }.use { context ->
            val dependency = MavenDependency(context.settings, "org.jetbrains.kotlin", "kotlin-test", "1.9.20")
            val extension = "module"
            val name = "${getNameWithoutExtension(dependency)}.$extension"
            val target = path.resolve(
                "${dependency.group}/${dependency.module}/${dependency.version}/3bf4b49eb37b4aca302f99bd99769f6e310bdb2/$name"
            )
            Files.createDirectories(target.parent)
            Path.of("testData/metadata/json/module/$name").copyTo(target)

            val dependencyFile = DependencyFile(dependency, getNameWithoutExtension(dependency), extension)
            assertTrue(runBlocking { dependencyFile.getPath()!!.startsWith(path) })

            val downloaded = runBlocking { dependencyFile.isDownloaded() }
            val hasMatchingChecksum = runBlocking { dependencyFile.hasMatchingChecksum(ResolutionLevel.LOCAL, context) }
            assertTrue(dependency.messages.isEmpty(), "There must be no messages: ${dependency.messages}")
            assertTrue(downloaded, "File must be downloaded as it was created above")
            assertTrue(hasMatchingChecksum, "File must have matching checksum as it was created above")
        }
    }

    @Test
    fun `jackson-module-kotlin-2_15_2_jar hash`() {
        val path = gradlePath
        Context {
            cache = {
                amperCache = amperPath
                localRepositories = listOf(MavenLocalRepository(path))
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings,
                "com.fasterxml.jackson.module", "jackson-module-kotlin", "2.15.2"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
                dependency.downloadDependencies(context)
            }
            val messages = dependency.messages.filter { "Downloaded from" !in it.text }
            assertTrue(messages.isEmpty(), "There must be no messages for $dependency: $messages")
        }
    }

    @Test
    fun `org_jetbrains_kotlinx kotlinx-datetime 0_5_0 with extra slash`() {
        val path = gradlePath
        Context {
            platforms = setOf(ResolutionPlatform.MACOS_X64)
            repositories = listOf("https://repo.maven.apache.org/maven2/")
            cache = {
                amperCache = amperPath
                localRepositories = listOf(MavenLocalRepository(path))
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings,
                "org.jetbrains.kotlinx", "kotlinx-datetime", "0.5.0"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
            }
            val errors = dependency.messages.filter { it.severity == Severity.ERROR }
            assertTrue(errors.isEmpty(), "There must be no errors: $errors")
        }
    }

    @Test
    fun `org_jetbrains_kotlinx kotlinx_coroutines_core 1_7_3 check metadata`() {
        val path = gradlePath
        Context {
            platforms = setOf(ResolutionPlatform.JVM, ResolutionPlatform.ANDROID)
            repositories = listOf("https://repo.maven.apache.org/maven2/")
            cache = {
                amperCache = amperPath
                localRepositories = listOf(MavenLocalRepository(path))
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings,
                "org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.7.3"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
            }
            val errors = dependency.messages.filter { it.severity == Severity.ERROR }
            assertTrue(errors.isEmpty(), "There must be no errors: $errors")


            val dependencyFile = DependencyFile(dependency, getNameWithoutExtension(dependency), "jar")
            assertTrue(runBlocking { dependencyFile.getPath()!!.startsWith(path) })

            val downloaded = runBlocking { dependencyFile.isDownloaded() }
            val hasMatchingChecksum = runBlocking { dependencyFile.hasMatchingChecksum(ResolutionLevel.LOCAL, context) }
            assertTrue(downloaded, "File must be downloaded as it was created above")
            assertTrue(hasMatchingChecksum, "File must have matching checksum as it was created above")
        }
    }

    @Test
    @Ignore
    fun `org_jetbrains_kotlinx kotlinx_datetime 0_4_0`() {
        val path = gradlePath
        Context {
            platforms = setOf(ResolutionPlatform.JVM, ResolutionPlatform.ANDROID)
            repositories = listOf("https://repo.maven.apache.org/maven2/")
            cache = {
                amperCache = amperPath
                localRepositories = listOf(MavenLocalRepository(path))
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings,
                "org.jetbrains.kotlinx", "kotlinx-datetime", "0.4.0"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
            }
            val errors = dependency.messages.filter { it.severity == Severity.ERROR }
            assertTrue(errors.isEmpty(), "There must be no errors: $errors")


            val dependencyFile = DependencyFile(dependency, "${getNameWithoutExtension(dependency)}-all", "jar")
            assertTrue(runBlocking { dependencyFile.getPath()!!.startsWith(path) })

            val downloaded = runBlocking { dependencyFile.isDownloaded() }
            val hasMatchingChecksum = runBlocking { dependencyFile.hasMatchingChecksum(ResolutionLevel.LOCAL, context) }
            assertTrue(downloaded, "File must be downloaded as it was created above")
            assertTrue(hasMatchingChecksum, "File must have matching checksum as it was created above")
        }
    }

//    @Test
    fun `org_jetbrains_kotlinx kotlinx-datetime 0_4_0 empty module file`() {
        val path = gradlePath
        Context {
            platforms = setOf(ResolutionPlatform.MACOS_ARM64)
            repositories = listOf("https://fake-repository/")
            cache = {
                amperCache = amperPath
                localRepositories = listOf(MavenLocalRepository(path))
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings,
                "org.jetbrains.kotlinx", "kotlinx-datetime", "0.4.0"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
            }
            val errors = dependency.messages.filter { it.severity == Severity.ERROR }
//            assertTrue(errors.isEmpty(), "There must be no errors: $errors")

            val dependencyFile = DependencyFile(dependency, getNameWithoutExtension(dependency), "module")
            val downloaded = runBlocking { dependencyFile.isDownloaded() }
            val hasMatchingChecksum = runBlocking { dependencyFile.hasMatchingChecksum(ResolutionLevel.LOCAL, context) }
            assertTrue(runBlocking { dependencyFile.getPath()!!.startsWith(path) })
            assertTrue(downloaded, "File must be downloaded as it was created above")
            assertTrue(hasMatchingChecksum, "File must have matching checksum as it was created above")
        }
    }

    @Test
    fun `org_jetbrains_kotlinx kotlinx_coroutines_core 1_7_3 check sourceSets`() {
        Context {
            platforms = setOf(
                ResolutionPlatform.JVM,
                ResolutionPlatform.ANDROID,
                ResolutionPlatform.IOS_ARM64,
                ResolutionPlatform.IOS_SIMULATOR_ARM64,
                ResolutionPlatform.IOS_X64,
            )
            scope = ResolutionScope.RUNTIME
            repositories = listOf("https://repo.maven.apache.org/maven2/")
            cache = {
                amperCache = amperPath
                localRepositories = listOf(MavenLocalRepository(gradlePath))
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings,
                "org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.7.3"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
            }
            val errors = dependency.messages.filter { it.severity == Severity.ERROR }
            assertTrue(errors.isEmpty(), "There must be no errors: $errors")

            assertEquals(
                setOf("commonMain", "concurrentMain"),
                dependency.files.map{ it.kmpSourceSet }.toSet(),
                "Unexpected list of resolved source sets"
            )

            val sourceSetFiles = runBlocking {
                dependency.files.associate { it.kmpSourceSet!! to it.getPath()!! }
            }

            // check sourceSet files content validity
            sourceSetFiles.forEach {
                val expectedSourceSetContent = expectedSourceSetsContent_KotlinCoroutine_1_7_3[it.key]!!
                JarFile(it.value.toFile()).use { jarFile ->
                    val entries: Enumeration<out ZipEntry?> = jarFile.entries()
                    entries.asIterator().forEach {
                        it?.let {
                            val size = expectedSourceSetContent[it.name]
                            assertNotNull(size, "Unexpected entry ${it.name}")
                            assertEquals(size, it.size,"Unexpected entry size")
                        }
                    }
                }
            }

            // check sha1 hash existence and validity
            sourceSetFiles.map { it.value }.forEach { sourceSetFile ->
                val sha1 = sourceSetFile.parent.resolve("${sourceSetFile.name}.sha1")
                assertTrue(sha1.exists(), "sha1 hash file is not found, it should be stored near the file for further validation")

                val calculatedHash = computeHash("sha1", sourceSetFile.readBytes())
                val storedHash = sha1.readText()
                assertEquals(calculatedHash, storedHash, "Wrong sha1 hash was stored")
            }
        }
    }

    private val expectedSourceSetsContent_KotlinCoroutine_1_7_3: Map<String, Map<String, Long>> = mapOf(
        "commonMain" to mapOf(
            "default/" to 0,
            "default/manifest" to 112,
            "default/linkdata/" to 0,
            "default/linkdata/module" to 281,
            "default/linkdata/package_kotlinx/" to 0,
            "default/linkdata/package_kotlinx/0_kotlinx.knm" to 19,
            "default/linkdata/package_kotlinx.coroutines/" to 0,
            "default/linkdata/package_kotlinx.coroutines/0_coroutines.knm" to 24021,
            "default/linkdata/package_kotlinx.coroutines/1_coroutines.knm" to 5302,
            "default/linkdata/package_kotlinx.coroutines/2_coroutines.knm" to 7048,
            "default/linkdata/package_kotlinx.coroutines/3_coroutines.knm" to 889,
            "default/linkdata/package_kotlinx.coroutines.channels/" to 0,
            "default/linkdata/package_kotlinx.coroutines.channels/0_channels.knm" to 16293,
            "default/linkdata/package_kotlinx.coroutines.channels/1_channels.knm" to 8985,
            "default/linkdata/package_kotlinx.coroutines.flow/" to 0,
            "default/linkdata/package_kotlinx.coroutines.flow/0_flow.knm" to 8693,
            "default/linkdata/package_kotlinx.coroutines.flow/1_flow.knm" to 12942,
            "default/linkdata/package_kotlinx.coroutines.flow/2_flow.knm" to 9138,
            "default/linkdata/package_kotlinx.coroutines.flow.internal/" to 0,
            "default/linkdata/package_kotlinx.coroutines.flow.internal/0_internal.knm" to 5284,
            "default/linkdata/package_kotlinx.coroutines.flow.internal/1_internal.knm" to 1975,
            "default/linkdata/package_kotlinx.coroutines.internal/" to 0,
            "default/linkdata/package_kotlinx.coroutines.internal/0_internal.knm" to 10331,
            "default/linkdata/package_kotlinx.coroutines.internal/1_internal.knm" to 3604,
            "default/linkdata/package_kotlinx.coroutines.intrinsics/" to 0,
            "default/linkdata/package_kotlinx.coroutines.intrinsics/0_intrinsics.knm" to 1319,
            "default/linkdata/package_kotlinx.coroutines.selects/" to 0,
            "default/linkdata/package_kotlinx.coroutines.selects/0_selects.knm" to 6805,
            "default/linkdata/package_kotlinx.coroutines.selects/1_selects.knm" to 1880,
            "default/linkdata/package_kotlinx.coroutines.sync/" to 0,
            "default/linkdata/package_kotlinx.coroutines.sync/0_sync.knm" to 4532,
            "default/linkdata/package_kotlinx.coroutines.sync/1_sync.knm" to 1053,
            "default/linkdata/root_package/" to 0,
            "default/linkdata/root_package/0_.knm" to 12,
            "default/resources/" to 0),
        "concurrentMain" to mapOf(
            "default/" to 0,
            "default/manifest" to 116,
            "default/linkdata/" to 0,
            "default/linkdata/module" to 142,
            "default/linkdata/package_kotlinx/" to 0,
            "default/linkdata/package_kotlinx/0_kotlinx.knm" to 19,
            "default/linkdata/package_kotlinx.coroutines/" to 0,
            "default/linkdata/package_kotlinx.coroutines/0_coroutines.knm" to 503,
            "default/linkdata/package_kotlinx.coroutines/1_coroutines.knm" to 1090,
            "default/linkdata/package_kotlinx.coroutines.channels/" to 0,
            "default/linkdata/package_kotlinx.coroutines.channels/0_channels.knm" to 655,
            "default/linkdata/package_kotlinx.coroutines.internal/" to 0,
            "default/linkdata/package_kotlinx.coroutines.internal/0_internal.knm" to 2251,
            "default/linkdata/package_kotlinx.coroutines.internal/1_internal.knm" to 573,
            "default/linkdata/root_package/" to 0,
            "default/linkdata/root_package/0_.knm" to 12,
            "default/resources/" to 0,
        )
    )

    @Test
    fun `org_jetbrains_compose_ui ui 1_5_10 kmp library resolution`() {
        val path = gradlePath
        Context {
            platforms = setOf(
                ResolutionPlatform.JVM,
                ResolutionPlatform.ANDROID,
                ResolutionPlatform.IOS_ARM64,
                ResolutionPlatform.IOS_SIMULATOR_ARM64,
                ResolutionPlatform.IOS_X64,
                )
            scope = ResolutionScope.RUNTIME
            repositories = listOf(
                "https://repo.maven.apache.org/maven2/")
            cache = {
                amperCache = amperPath
                localRepositories = listOf(MavenLocalRepository(path))
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings,
                "org.jetbrains.compose.ui", "ui", "1.5.10"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
            }
            val errors = dependency.messages.filter { it.severity == Severity.ERROR }
            assertTrue(errors.isEmpty(), "There must be no errors: $errors")

            assertEquals(setOf("commonMain"),
                dependency.files.map{ it.kmpSourceSet }.toSet(),
                "Unexpected list of resolved source sets"
            )
        }
    }
}
