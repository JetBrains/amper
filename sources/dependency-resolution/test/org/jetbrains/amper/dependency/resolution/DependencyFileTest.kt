/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.dependency.resolution.BaseDRTest.Companion.REDIRECTOR_COMPOSE_DEV
import org.jetbrains.amper.dependency.resolution.BaseDRTest.Companion.REDIRECTOR_MAVEN_CENTRAL
import org.jetbrains.amper.dependency.resolution.BaseDRTest.Companion.REDIRECTOR_MAVEN_GOOGLE
import org.jetbrains.amper.dependency.resolution.BaseDRTest.Companion.defaultFilterMessages
import org.jetbrains.amper.dependency.resolution.BaseDRTest.Companion.verifyMessages
import org.jetbrains.amper.dependency.resolution.BaseDRTest.Companion.verifyOwnMessages
import org.jetbrains.amper.dependency.resolution.diagnostics.PlatformsAreNotSupported
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.dependency.resolution.diagnostics.detailedMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import kotlin.io.path.Path
import kotlin.io.path.appendText
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DependencyFileTest {
    @TempDir
    lateinit var temp: Path

    private val amperPath: Path
        get() = temp / ".amper"

    private val gradleLocalPath: Path
        get() = temp / ".gradle"

    private val mavenLocalPath: Path
        get() = temp / ".m2.cache"

    private fun gradleLocalRepository() = GradleLocalRepository(gradleLocalPath)
    private fun mavenLocalRepository() = MavenLocalRepository(mavenLocalPath)

    @Test
    fun `kotlin-test-1_9_20 module hash`() {
        val gradleLocalRepository = gradleLocalRepository()
        Context {
            cache = {
                amperCache = amperPath
                localRepository = gradleLocalRepository()
                readOnlyExternalRepositories = emptyList()
            }
        }.use { context ->
            val dependency = MavenDependency(context.settings, "org.jetbrains.kotlin", "kotlin-test", "1.9.20")
            val extension = "module"
            val name = "${getNameWithoutExtension(dependency)}.$extension"
            val target = gradleLocalRepository.getPath(dependency, name, "3bf4b49eb37b4aca302f99bd99769f6e310bdb2")
            target.parent.createDirectories()
            Path("testData/metadata/json/module/$name").copyTo(target)

            val dependencyFile = DependencyFile(dependency, getNameWithoutExtension(dependency), extension)
            assertTrue(runBlocking { dependencyFile.getPath()!!.startsWith(gradleLocalPath) })

            val downloaded = runBlocking { dependencyFile.isDownloaded() }
            val hasMatchingChecksum = runBlocking { dependencyFile.hasMatchingChecksumLocally(context = context) }
            assertTrue(dependency.messages.isEmpty(), "There must be no messages: ${dependency.messages}")
            assertTrue(downloaded, "File must be downloaded as it was created above")
            assertTrue(hasMatchingChecksum, "File must have matching checksum as it was created above")
        }
    }

    @Test
    fun `jackson-module-kotlin-2_15_2_jar hash`() {
        Context {
            cache = {
                amperCache = amperPath
                localRepository = mavenLocalRepository()
                readOnlyExternalRepositories = emptyList()
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
            dependency.verifyOwnMessages()
        }
    }

    @Test
    fun `org_junit_jupiter junit-jupiter-api 9999 diagnostic error message NETWORK level`() {
        Context {
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_COMPOSE_DEV).toRepositories()
            cache = {
                amperCache = amperPath
                localRepository = mavenLocalRepository()
                readOnlyExternalRepositories = emptyList()
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings,
                "org.junit.jupiter", "junit-jupiter-api", "9999"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
                dependency.downloadDependencies(context)
            }
            val messages = dependency.messages.defaultFilterMessages()
            assertEquals(
                setOf("Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999"),
                messages.map { it.message }.toSet(),
                "Unexpected diagnostic messages")

            assertEquals(
                setOf("""
                    Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999
                      Unable to download checksums of file junit-jupiter-api-9999.pom
                      Unable to download checksums of file junit-jupiter-api-9999.module
                    Repositories used for resolution:
                      - https://cache-redirector.jetbrains.com/repo1.maven.org/maven2
                      - https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/public/p/compose/dev
                """.trimIndent()),
                messages.map{ it.detailedMessage }.toSet(),
                "Unexpected diagnostic messages")
        }
    }

    @Test
    fun `org_junit_jupiter junit-jupiter-api 9999 diagnostic error message LOCAL level`() {
        Context {
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_COMPOSE_DEV).toRepositories()
            cache = {
                amperCache = amperPath
                localRepository = mavenLocalRepository()
                readOnlyExternalRepositories = emptyList()
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings,
                "org.junit.jupiter", "junit-jupiter-api", "9999"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.LOCAL)
                dependency.downloadDependencies(context)
            }
            val messages = dependency.messages.defaultFilterMessages().map{ it.message }.toSet()
            assertEquals(
                setOf("Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999"),
                messages,
                "Unexpected diagnostic messages")
        }
    }

    /**
     * Test checks that DR fails if library doesn't declare variant for required platform
     * 'org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.8.22' depends on 'org.jetbrains.skiko:skiko-awt:0.8.22'
     * that define jvm-related variants only
     */
    @Test
    fun `skiko-awt-runtime-macos-arm64-0_8_22_jar hash`() {
        Context {
            cache = {
                amperCache = amperPath
                localRepository = mavenLocalRepository()
                readOnlyExternalRepositories = emptyList()
            }
            repositories = listOf(REDIRECTOR_COMPOSE_DEV).toRepositories()
            platforms = setOf(ResolutionPlatform.MACOS_ARM64)
        }.use { context ->
            val dependency = MavenDependency(
                context.settings,
                "org.jetbrains.skiko", "skiko-awt-runtime-macos-arm64", "0.8.22"
            )
            val root = MavenDependencyNode(context, dependency)
            runBlocking {
                val resolver = Resolver()
                resolver.buildGraph(root)
                resolver.downloadDependencies(root)
            }
            root.distinctBfsSequence().forEach {
                if (it is MavenDependencyNode && it.module.startsWith("skiko-awt")) {
                    val messages = it.messages.defaultFilterMessages()
                    assertEquals(1, messages.size,
                        "There must be the only error messages instead of ${messages.size}: $messages")
                    val message = assertIs<PlatformsAreNotSupported>(messages.single(),"Unexpected error message")
                    assertEquals(setOf(ResolutionPlatform.MACOS_ARM64), message.unsupportedPlatforms)
                    assertEquals(setOf(ResolutionPlatform.JVM, ResolutionPlatform.ANDROID), message.supportedPlatforms)
                } else {
                    it.verifyOwnMessages()
                }
            }
        }
    }

    @Test
    fun `skiko-0_8_4_jar macOS platforms`() {
        Context {
            cache = {
                amperCache = amperPath
                localRepository = mavenLocalRepository()
                readOnlyExternalRepositories = emptyList()
            }
            repositories = listOf(
                REDIRECTOR_MAVEN_CENTRAL,
                REDIRECTOR_MAVEN_GOOGLE,
                REDIRECTOR_COMPOSE_DEV
            ).toRepositories()
            platforms = setOf(ResolutionPlatform.IOS_SIMULATOR_ARM64, ResolutionPlatform.IOS_ARM64)
        }.use { context ->
            val dependency = MavenDependency(
                context.settings,
                "org.jetbrains.skiko", "skiko", "0.8.4"
            )
            val root = MavenDependencyNode(context, dependency)
            runBlocking {
                val resolver = Resolver()
                resolver.buildGraph(root)
                resolver.downloadDependencies(root)
            }
            root.verifyMessages()
        }
    }

    @Test
    fun `compose-multiplatform iosSimulatorArm64`() {
        Context {
            cache = {
                amperCache = amperPath
                localRepository = MavenLocalRepository(mavenLocalPath.resolve(UUID.randomUUID().toString()))
                readOnlyExternalRepositories = emptyList()
            }
            repositories = listOf(
                REDIRECTOR_MAVEN_CENTRAL,
                REDIRECTOR_MAVEN_GOOGLE,
                REDIRECTOR_COMPOSE_DEV
            ).toRepositories()
            platforms = setOf(ResolutionPlatform.IOS_SIMULATOR_ARM64)
        }.use { context ->
            val root = DependencyNodeHolder(
                "root",
                listOf(
                    context.getOrCreateNode(
                        context.createOrReuseDependency(
                            "org.jetbrains.compose.foundation", "foundation", "1.6.10"
                        ), null
                    ),
                    context.getOrCreateNode(
                        context.createOrReuseDependency(
                            "org.jetbrains.compose.material3", "material3", "1.6.10"
                        ), null
                    ),
                    context.getOrCreateNode(
                        context.createOrReuseDependency(
                            "org.jetbrains.kotlin", "kotlin-stdlib", "2.0.0"
                        ), null
                    ),
                ),
                context
            )
            runBlocking {
                val resolver = Resolver()
                resolver.buildGraph(root)
                resolver.downloadDependencies(root)
            }
            root.verifyMessages()
        }
    }

    @Test
    fun `skiko-0_8_4_jar iosSimulatorArm64`() {
        Context {
            cache = {
                amperCache = amperPath
                localRepository = mavenLocalRepository()
                readOnlyExternalRepositories = emptyList()
            }
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE, REDIRECTOR_COMPOSE_DEV)
                .toRepositories()
            platforms = setOf(ResolutionPlatform.IOS_SIMULATOR_ARM64)
            scope = ResolutionScope.RUNTIME
        }.use { context ->
            val dependency = MavenDependency(
                context.settings,
                "org.jetbrains.skiko", "skiko", "0.8.4"
            )
            val root = MavenDependencyNode(context, dependency)
            runBlocking {
                val resolver = Resolver()
                resolver.buildGraph(root)
                resolver.downloadDependencies(root)
            }
            root.verifyMessages()
        }
    }

    @Test
    fun `org_jetbrains_kotlinx kotlinx-datetime 0_5_0 with extra slash`() {
        Context {
            platforms = setOf(ResolutionPlatform.MACOS_X64)
            repositories = listOf("$REDIRECTOR_MAVEN_CENTRAL/").toRepositories()
            cache = {
                amperCache = amperPath
                localRepository = mavenLocalRepository()
                readOnlyExternalRepositories = emptyList()
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
    fun `org_jetbrains_kotlinx kotlinx_coroutines_core 1_9_0 check metadata`() {
        Context {
            platforms = setOf(ResolutionPlatform.JVM, ResolutionPlatform.ANDROID)
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL).toRepositories()
            cache = {
                amperCache = amperPath
                localRepository = mavenLocalRepository()
                readOnlyExternalRepositories = emptyList()
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings,
                "org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.9.0"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
            }
            val errors = dependency.messages.filter { it.severity == Severity.ERROR }
            assertTrue(errors.isEmpty(), "There must be no errors: $errors")

            val dependencyFile = DependencyFile(dependency, getNameWithoutExtension(dependency), "jar")
            assertTrue(runBlocking { dependencyFile.getPath()!!.startsWith(mavenLocalPath) })

            val downloaded = runBlocking { dependencyFile.isDownloaded() }
            val hasMatchingChecksum = runBlocking { dependencyFile.hasMatchingChecksumLocally(context = context) }
            assertTrue(downloaded, "File must be downloaded as it was created above")
            assertTrue(hasMatchingChecksum, "File must have matching checksum as it was created above")
        }
    }

    @Test
    fun `artifact is re-downloaded if it has incorrect checksum`() {
        Context {
            platforms = setOf(ResolutionPlatform.JVM)
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL).toRepositories()
            cache = {
                amperCache = amperPath
                localRepository = mavenLocalRepository()
                readOnlyExternalRepositories = emptyList()
            }
        }.use { context ->
            val dependencyNode = context.getOrCreateNode(
                context.createOrReuseDependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"), null
            )

            runBlocking { Resolver().buildGraph(dependencyNode) }

            val errors = dependencyNode.dependency.messages.filter { it.severity == Severity.ERROR }
            assertTrue(errors.isEmpty(), "There must be no errors: $errors")

            runBlocking { Resolver().downloadDependencies(dependencyNode) }

            val dependencyFile = DependencyFile(dependencyNode.dependency, getNameWithoutExtension(dependencyNode.dependency), "jar")
            val path = runBlocking { dependencyFile.getPath()!! }
            assertTrue(path.startsWith(mavenLocalPath))

            val downloaded = runBlocking { dependencyFile.isDownloaded() }
            val hasMatchingChecksum = runBlocking { dependencyFile.hasMatchingChecksumLocally(context = context) }
            assertTrue(downloaded, "File must have be downloaded")
            assertTrue(hasMatchingChecksum, "File must have matching checksum as it was just downloaded")

            // Update the locally stored file so that its checksum is no longer correct
            path.appendText("Now artifact from local storage have incorrect checksum and should be re-downloaded")
            val hasMatchingChecksumAfterCorruption = runBlocking { dependencyFile.hasMatchingChecksumLocally(context = context) }
            assertFalse (hasMatchingChecksumAfterCorruption, "File was corrupted, checksum check should have failed")

            // Check that artifact was successfully re-downloaded
            runBlocking { Resolver().downloadDependencies(dependencyNode) }
            assertTrue(dependencyNode.dependency.messages.none { it.severity == Severity.ERROR }, "There must be no errors: ${dependencyNode.dependency.messages}")
            val hasMatchingChecksumAfterReDownloading = runBlocking { dependencyFile.hasMatchingChecksumLocally(context = context) }
            assertTrue(hasMatchingChecksumAfterReDownloading, "File must have been re-downloaded and should have valid checksum")
        }
    }

    @Test
    fun `obsolete diagnostic errors from LOCAL run are suppressed after artifact is re-downloaded`() {
        Context {
            platforms = setOf(ResolutionPlatform.JVM)
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL).toRepositories()
            cache = {
                amperCache = amperPath
                localRepository = mavenLocalRepository()
                readOnlyExternalRepositories = emptyList()
            }
        }.use { context ->
            val dependencyNode = MavenDependencyNode(context,
                MavenDependency(context.settings, "org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.7.3")
            )

            runBlocking { Resolver().buildGraph(dependencyNode) }

            val errors = dependencyNode.dependency.messages.filter { it.severity == Severity.ERROR }
            assertTrue(errors.isEmpty(), "There must be no errors: $errors")

            runBlocking { Resolver().downloadDependencies(dependencyNode) }

            val dependencyFile = DependencyFile(dependencyNode.dependency, getNameWithoutExtension(dependencyNode.dependency), "module")
            val path = runBlocking { dependencyFile.getPath()!! }
            assertTrue(path.startsWith(mavenLocalPath))

            val downloaded = runBlocking { dependencyFile.isDownloaded() }
            val hasMatchingChecksum = runBlocking { dependencyFile.hasMatchingChecksumLocally(context = context) }
            assertTrue(downloaded, "File must have be downloaded")
            assertTrue(hasMatchingChecksum, "File must have matching checksum as it was just downloaded")

            // Update the locally stored file so that its checksum is no longer correct
            path.appendText("Now artifact from local storage have incorrect checksum and should be re-downloaded")
            val hasMatchingChecksumAfterCorruption = runBlocking { dependencyFile.hasMatchingChecksumLocally(context = context) }
            assertFalse (hasMatchingChecksumAfterCorruption, "File was corrupted, checksum check should have failed")

            val dependencyNodeDuplicate = MavenDependencyNode(context,
                MavenDependency(context.settings, "org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.7.3")
            )
            val dependencyFileDuplicate = DependencyFile(dependencyNodeDuplicate.dependency, getNameWithoutExtension(dependencyNodeDuplicate.dependency), "module")

            // Check that artifact was not successfully re-downloaded during LOCAL run
            runBlocking { Resolver().buildGraph(dependencyNodeDuplicate, level = ResolutionLevel.LOCAL) }
            assertTrue(dependencyNodeDuplicate.dependency.messages.any{ it.severity >= Severity.WARNING }, "There must be an errors about incorrect module file: ${dependencyNodeDuplicate.dependency.messages}")

            // Check that artifact was successfully re-downloaded after NETWORK run and no errors are left
            runBlocking { Resolver().buildGraph(dependencyNodeDuplicate, level = ResolutionLevel.NETWORK) }
            assertTrue(dependencyNodeDuplicate.dependency.messages.none { it.severity == Severity.ERROR }, "There must be no errors: ${dependencyNodeDuplicate.dependency.messages}")
            val hasMatchingChecksumAfterReDownloading = runBlocking { dependencyFileDuplicate.hasMatchingChecksumLocally(context = context) }
            assertTrue(hasMatchingChecksumAfterReDownloading, "File must have been re-downloaded and should have valid checksum")
        }
    }

    @Test
    fun `org_jetbrains_kotlinx kotlinx_datetime 0_4_0`() {
        Context {
            platforms = setOf(ResolutionPlatform.JVM, ResolutionPlatform.ANDROID)
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL).toRepositories()
            cache = {
                amperCache = amperPath
                localRepository = mavenLocalRepository()
                readOnlyExternalRepositories = emptyList()
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
            assertTrue(runBlocking { dependencyFile.getPath()!!.startsWith(mavenLocalPath) })

            val downloaded = runBlocking { dependencyFile.isDownloaded() }
            val hasMatchingChecksum = runBlocking { dependencyFile.hasMatchingChecksumLocally(context = context) }
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
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL).toRepositories()
            cache = {
                amperCache = amperPath
                localRepository = mavenLocalRepository()
                readOnlyExternalRepositories = emptyList()
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
                dependency.files().map{ it.kmpSourceSet }.toSet(),
                "Unexpected list of resolved source sets"
            )

            val sourceSetFiles = runBlocking {
                dependency.files().associate { it.kmpSourceSet!! to it.getPath()!! }
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
        Context {
            platforms = setOf(
                ResolutionPlatform.JVM,
                ResolutionPlatform.ANDROID,
                ResolutionPlatform.IOS_ARM64,
                ResolutionPlatform.IOS_SIMULATOR_ARM64,
                ResolutionPlatform.IOS_X64,
                )
            scope = ResolutionScope.RUNTIME
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL).toRepositories()
            cache = {
                amperCache = amperPath
                localRepository = mavenLocalRepository()
                readOnlyExternalRepositories = emptyList()
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
                dependency.files().map{ it.kmpSourceSet }.toSet(),
                "Unexpected list of resolved source sets"
            )
        }
    }
}
