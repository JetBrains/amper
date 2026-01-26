/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalRepositoryTest : BaseDRTest() {
    @TempDir
    lateinit var cacheRoot: Path

    @Test
    fun `check maven local storage is reused if primary storage misses artifacts`() {
        checkLocalRepositoryUsage(
            // All of those artifacts are taken from maven local storage
            filesThatShouldNotBeDownloaded = listOf(
                "atomicfu-jvm-0.23.2.pom",
                "atomicfu-jvm-0.23.2.module",
                "atomicfu-jvm-0.23.2.jar",
            ),
            // Checksums should be downloaded and checked even if those are presented in maven local storage
            filesThatMustBeDownloaded = listOf(
                "atomicfu-jvm-0.23.2.pom.sha256",
                "atomicfu-jvm-0.23.2.module.sha256"
            )
        )
    }

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `check gradle local storage is reused if primary storage misses artifacts`() {
        checkLocalRepositoryUsage(
            // All of those artifacts are taken from maven local storage
            filesThatShouldNotBeDownloaded = listOf(
                "atomicfu-jvm-0.23.2.pom",
                "atomicfu-jvm-0.23.2.module",
                "atomicfu-jvm-0.23.2.jar",
            ),
            // Checksums should be downloaded and checked even if those are presented in maven local storage
            filesThatMustBeDownloaded = listOf(
                "atomicfu-jvm-0.23.2.pom.sha256",
                "atomicfu-jvm-0.23.2.module.sha256"
            ),
            initLocalRepository = { cacheRoot -> initEtalonGradleLocalStorage(cacheRoot) }
        )
    }

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `check gradle local storage is ignored if primary storage misses artifact, but its checksum doesn't match`() {
        checkLocalRepositoryUsage(
            filesThatShouldNotBeDownloaded = listOf(
                "atomicfu-jvm-0.23.2.pom",
                "atomicfu-jvm-0.23.2.module",
            ),
            // Jar file has incorrect checksums in maven local storage, => we ignore it
            // Checksums are not taken from maven local storage
            filesThatMustBeDownloaded = listOf(
                "atomicfu-jvm-0.23.2.jar",
                "atomicfu-jvm-0.23.2.pom.sha256",
                "atomicfu-jvm-0.23.2.module.sha256"
            ),
            // Corrupt JAR file stored in maven local storage, => it should be re-downloaded
            updateLocalRepository = { gradleLocalRepository ->
                val gradleLocalPath = (gradleLocalRepository as GradleLocalRepository).filesPath
                val atomicfuJarPath =
                    gradleLocalPath.resolve("org.jetbrains.kotlinx/atomicfu-jvm/0.23.2/a4601dc42dceb031a586058e8356ff778a57dea0/atomicfu-jvm-0.23.2.jar")
                assertTrue(atomicfuJarPath.exists())
                atomicfuJarPath.appendText("No artifact from local storage have incorrect checksum and should be ignored")
            },
            initLocalRepository = { cacheRoot -> initEtalonGradleLocalStorage(cacheRoot) }
        )
    }

    @Test
    fun `check maven local storage is ignored if primary storage misses artifact, but its checksum doesn't match`() = runTest {
        checkLocalRepositoryUsage(
            filesThatShouldNotBeDownloaded = listOf(
                "atomicfu-jvm-0.23.2.pom",
                "atomicfu-jvm-0.23.2.module"
            ),
            // Jar file has an incorrect checksums in maven local storage, => we ignore it
            // Checksums are not taken from maven local storage
            filesThatMustBeDownloaded = listOf(
                "atomicfu-jvm-0.23.2.jar",
                "atomicfu-jvm-0.23.2.pom.sha256",
                "atomicfu-jvm-0.23.2.module.sha256"
            ),
            // Corrupt JAR file stored in maven local storage, => it should be re-downloaded
            updateLocalRepository = { mavenLocalRepository ->
                val mavenLocalPath = (mavenLocalRepository as MavenLocalRepository).repository
                val atomicfuJarPath =
                    mavenLocalPath.resolve("org/jetbrains/kotlinx/atomicfu-jvm/0.23.2/atomicfu-jvm-0.23.2.jar")
                assertTrue(atomicfuJarPath.exists())
                atomicfuJarPath.appendText("No artifact from local storage have incorrect checksum and should be ignored")
            },
            initLocalRepository = { cacheRoot -> initEtalonMavenLocalStorage(cacheRoot) }
        )
    }

    private suspend fun initEtalonGradleLocalStorage(cacheRoot: Path): LocalRepository {
        // Installing Gradle local repository at the custom location
        cacheRoot.createDirectories()

        val gradleLocal = GradleLocalRepository(cacheRoot)

        initEtalonLocalStorage(cacheRoot, gradleLocal)

        return gradleLocal
    }

    private suspend fun initEtalonMavenLocalStorage(cacheRoot: Path): LocalRepository {
        // Installing maven local repository at the custom location
        cacheRoot.createDirectories()

        val mavenLocal = MavenLocalRepository(cacheRoot)

        initEtalonLocalStorage(cacheRoot, mavenLocal)
        return mavenLocal
    }

    private suspend fun initEtalonLocalStorage(localStoragePath: Path, localStorage: LocalRepository) {
        val atomicfuCoordinates = "org.jetbrains.kotlinx:atomicfu-jvm:0.23.2"

        // Initialize resolution context
        val context = context(ResolutionScope.COMPILE, cacheBuilder = {
            amperCache = localStoragePath.resolve(".amper")
            localRepository = localStorage
            readOnlyExternalRepositories = emptyList()
        })

        val root = RootDependencyNodeWithContext(
            children = listOf(atomicfuCoordinates.toMavenNode(context)),
            templateContext = context()
        )

        doTest(
            root, expected = """
                root
                ╰─── org.jetbrains.kotlinx:atomicfu-jvm:0.23.2
                """.trimIndent()
        )

        downloadAndAssertFiles(listOf("atomicfu-jvm-0.23.2.jar"), root)
    }

    private fun checkLocalRepositoryUsage(
        filesThatShouldNotBeDownloaded: List<String> = emptyList(),
        filesThatMustBeDownloaded: List<String> = emptyList(),
        updateLocalRepository: (LocalRepository) -> Unit = {},
        initLocalRepository: suspend (Path) -> LocalRepository = { cacheRoot -> initEtalonMavenLocalStorage(cacheRoot) }
    ) = runTest {
        val atomicfuCoordinates = "org.jetbrains.kotlinx:atomicfu-jvm:0.23.2"
        val urlPrefix =
            "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/atomicfu-jvm/0.23.2"

        // Installing maven local repository at the custom location
        val localExternalRepository = initLocalRepository(cacheRoot)

        // Initialize resolution context
        val cacheBuilder = withLocalRepository(cacheRoot, localExternalRepository)
        val cache = FileCacheBuilder(cacheBuilder).build()
        val context = context(ResolutionScope.COMPILE, cacheBuilder = cacheBuilder)
        val httpClient = TestHttpClient.create(
            filesThatShouldNotBeDownloaded.map { "$urlPrefix/$it" }
        )
        context.resolutionCache.computeIfAbsent(httpClientKey) { httpClient }

        val nodeInCompileContext = atomicfuCoordinates.toMavenNode(context)

        val root = RootDependencyNodeWithContext(
            children = listOf(nodeInCompileContext),
            templateContext = context()
        )

        updateLocalRepository(localExternalRepository)

        assertFalse(
            (cache.localRepository as MavenLocalRepository).repository.resolve("org/jetbrains/kotlinx/atomicfu-jvm")
                .exists(),
            "Local repository should not contain atomicfu-jvm"
        )

        doTest(
            root,
            expected = """
                    root
                    ╰─── org.jetbrains.kotlinx:atomicfu-jvm:0.23.2
                """.trimIndent()
        )

        assertTrue(
            cache.localRepository.repository.resolve("org/jetbrains/kotlinx/atomicfu-jvm")
                .exists(),
            "Local repository should not contain atomicfu-jvm"
        )

        downloadAndAssertFiles(listOf("atomicfu-jvm-0.23.2.jar"), root)

        val notDownloaded =
            filesThatMustBeDownloaded.filterNot { URI.create("$urlPrefix/$it") in httpClient.processedUrls }
        assertTrue(
            notDownloaded.isEmpty(),
            "The following artifacts should have been downloaded, but they were not: ${notDownloaded.joinToString("\n")}"
        )
    }

    /**
     * Create Amper like configuration of repositories.
     * Maven local repository ir reused.
     */
    fun withLocalRepository(
        cacheRoot: Path,
        localReadOnlyExternalRepository: LocalRepository
    ): FileCacheBuilder.() -> Unit = {
        amperCache = cacheRoot
        localRepository = MavenLocalRepository(cacheRoot.resolve(".m2.cache.test"))
        readOnlyExternalRepositories = listOf(localReadOnlyExternalRepository)
    }
}
