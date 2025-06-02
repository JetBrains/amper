/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.dependency.resolution.diagnostics.UnableToResolveDependency
import org.jetbrains.amper.dependency.resolution.metadata.xml.SnapshotVersion
import org.jetbrains.amper.dependency.resolution.metadata.xml.parseMetadata
import org.jetbrains.amper.dependency.resolution.metadata.xml.serialize
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import uk.org.webcompere.systemstubs.properties.SystemProperties
import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpClient.Redirect
import java.net.http.HttpClient.Version
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResolveFromMavenLocalTest : BaseDRTest() {

    override val testDataPath: Path = super.testDataPath / "mavenLocal" / "goldenFiles"
    private val mavenLocalTestDataPath: Path = super.testDataPath / "mavenLocal" / "repository"

    @TempDir
    lateinit var tmpDir: Path

    private fun uniqueCacheRoot() = (tmpDir / UUID.randomUUID().toString().substring(0, 8)).createDirectories()

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `release artifacts are resolved from mavenLocal`(
        systemProperties: SystemProperties,
        environmentVariables: EnvironmentVariables,
        testInfo: TestInfo,
    ) {
        clearLocalM2MachineOverrides(systemProperties, environmentVariables)

        val testCacheRoot = uniqueCacheRoot()

        val mavenLocalPath = testCacheRoot.resolve("maven")

        try {
            systemProperties.set("maven.repo.local", mavenLocalPath.pathString)
            val repo = MavenLocalRepository()
            kotlin.test.assertEquals(repo.repository, testCacheRoot / "maven")

            checkLocalRepositoryUsage(
                testInfo,
                "org.jetbrains.kotlinx:atomicfu-jvm:0.23.2".toMavenCoordinates(),
                testCacheRoot,
                // All of those artifacts are taken from maven local storage
                filesThatShouldNotBeDownloaded = listOf(
                    "atomicfu-jvm-0.23.2.pom",
                    "atomicfu-jvm-0.23.2.module",
                    "atomicfu-jvm-0.23.2.jar",
                    "atomicfu-jvm-0.23.2.pom.sha256",
                    "atomicfu-jvm-0.23.2.module.sha256"
                ),
                // Checksums are ignored when an artifact is resolved from mavenLocal
                filesThatMustBeDownloaded = emptyList(),
                repositories = listOf(MAVEN_LOCAL),
                initLocalRepository = { initEtalonMavenLocalStorage(mavenLocalPath) }
            )
        } finally {
            System.clearProperty("maven.repo.local")
        }
    }

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `SNAPSHOT artifacts are resolved from mavenLocal`(
        systemProperties: SystemProperties,
        environmentVariables: EnvironmentVariables,
        testInfo: TestInfo,
    ) {
        clearLocalM2MachineOverrides(systemProperties, environmentVariables)

        val testCacheRoot = uniqueCacheRoot()

        val mavenLocalPath = testCacheRoot.resolve("maven").createDirectories()

        try {
            systemProperties.set("maven.repo.local", mavenLocalPath.pathString)
            val repo = MavenLocalRepository()
            kotlin.test.assertEquals(repo.repository, testCacheRoot / "maven")

            val root = checkLocalRepositoryUsage(
                testInfo,
                "org.jetbrains:dr-snapshot-sample:1.0-SNAPSHOT".toMavenCoordinates(),
                testCacheRoot,
                // All of those artifacts are taken from mavenLocal storage
                filesThatShouldNotBeDownloaded = listOf(
                    "dr-snapshot-sample-1.0-SNAPSHOT.pom",
                    "dr-snapshot-sample-1.0-SNAPSHOT.module",
                    "dr-snapshot-sample-1.0-SNAPSHOT.jar",
                ),
                // transitive dependencies that are missing in mavenLocal
                filesThatMustBeDownloaded = listOf(
                    "jackson-annotations-2.18.2.jar",
                    "jackson-annotations-2.18.2.pom",
                    "jackson-annotations-2.18.2.pom.sha512",
                    "jackson-annotations-2.18.2.module.sha512",
                ),
                repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, MAVEN_LOCAL),
                initLocalRepository = {
                    mavenLocalTestDataPath.copyToRecursively(mavenLocalPath, followLinks = false, overwrite = false)

                    val mavenMetadataLocal = mavenLocalPath.resolve(
                        "org/jetbrains/dr-snapshot-sample/1.0-SNAPSHOT/maven-metadata-local.xml")

                    setupMavenMetadataDates(mavenMetadataLocal)

                    repo
                }
            )
        } finally {
            System.clearProperty("maven.repo.local")
        }
    }

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `SNAPSHOT artifacts can not be resolved from mavenLocal if maven-metadata-local xml is absent`(
        systemProperties: SystemProperties,
        environmentVariables: EnvironmentVariables,
        testInfo: TestInfo,
    ) {
        clearLocalM2MachineOverrides(systemProperties, environmentVariables)

        val testCacheRoot = uniqueCacheRoot()

        val mavenLocalPath = testCacheRoot.resolve("maven").createDirectories()

        try {
            systemProperties.set("maven.repo.local", mavenLocalPath.pathString)
            val repo = MavenLocalRepository()
            kotlin.test.assertEquals(repo.repository, testCacheRoot / "maven")

            val root = checkLocalRepositoryUsage(
                testInfo,
                "org.jetbrains:dr-snapshot-sample:1.0-SNAPSHOT".toMavenCoordinates(),
                testCacheRoot,
                verifyMessages = false,
                // All of those artifacts are taken from mavenLocal storage
                filesThatShouldNotBeDownloaded = listOf(
                    "dr-snapshot-sample-1.0-SNAPSHOT.pom",
                    "dr-snapshot-sample-1.0-SNAPSHOT.module",
                    "dr-snapshot-sample-1.0-SNAPSHOT.jar",
                ),
                filesThatMustBeDownloaded = emptyList(),
                repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, MAVEN_LOCAL),
                initLocalRepository = {
                    mavenLocalTestDataPath.copyToRecursively(mavenLocalPath, followLinks = false, overwrite = false)

                    val mavenMetadataLocal = mavenLocalPath.resolve(
                        "org/jetbrains/dr-snapshot-sample/1.0-SNAPSHOT/maven-metadata-local.xml")

                    setupMavenMetadataDates(mavenMetadataLocal)

                    // Moving maven-metadata-local.xml to maven-metadata-repositoryId.xml.
                    // It means that in spite SNAPSHOT artifact is there, it should not be used,
                    // because it was downloaded from the external repository with id equal to 'repositoryId',
                    // such an artifact should not be used (it wasn't installed into mavenLocal)
                    mavenMetadataLocal.moveTo(mavenMetadataLocal.parent.resolve("maven-metadata-repositoryId.xml"), overwrite = false)

                    repo
                }
            )

            assertTheOnlyNonInfoMessage<UnableToResolveDependency>(root, Severity.ERROR)
        } finally {
            System.clearProperty("maven.repo.local")
        }
    }

    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `SNAPSHOT artifacts can not be resolved from mavenLocal if maven-metadata-local contains outdated timestamps`(
        systemProperties: SystemProperties,
        environmentVariables: EnvironmentVariables,
        testInfo: TestInfo,
    ) {
        clearLocalM2MachineOverrides(systemProperties, environmentVariables)

        val testCacheRoot = uniqueCacheRoot()

        val mavenLocalPath = testCacheRoot.resolve("maven").createDirectories()

        try {
            systemProperties.set("maven.repo.local", mavenLocalPath.pathString)
            val repo = MavenLocalRepository()
            kotlin.test.assertEquals(repo.repository, testCacheRoot / "maven")

            val root = checkLocalRepositoryUsage(
                testInfo,
                "org.jetbrains:dr-snapshot-sample:1.0-SNAPSHOT".toMavenCoordinates(),
                testCacheRoot,
                verifyMessages = false,
                // All of those artifacts are taken from mavenLocal storage
                filesThatShouldNotBeDownloaded = listOf(
                    "dr-snapshot-sample-1.0-SNAPSHOT.pom",
                    "dr-snapshot-sample-1.0-SNAPSHOT.module",
                    "dr-snapshot-sample-1.0-SNAPSHOT.jar",
                ),
                filesThatMustBeDownloaded = emptyList(),
                repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, MAVEN_LOCAL),
                initLocalRepository = {
                    mavenLocalTestDataPath.copyToRecursively(mavenLocalPath, followLinks = false, overwrite = false)

                    val mavenMetadataLocal = mavenLocalPath.resolve(
                        "org/jetbrains/dr-snapshot-sample/1.0-SNAPSHOT/maven-metadata-local.xml")

                    setupMavenMetadataDates(mavenMetadataLocal, valid = false)

                    repo
                }
            )

            assertTheOnlyNonInfoMessage<UnableToResolveDependency>(root, Severity.ERROR)
        } finally {
            System.clearProperty("maven.repo.local")
        }
    }

    private fun setupMavenMetadataDates(mavenMetadataLocalPath: Path, valid: Boolean = true) {
        assertTrue(mavenMetadataLocalPath.exists(), "${mavenMetadataLocalPath.fileName} is not found")

        val mavenMetadataLocal = try {
            mavenMetadataLocalPath.readText().parseMetadata()
        } catch (e: AmperDependencyResolutionException) {
            fail("Unexpected exception on parsing maven-metadata", e)
        }
        val snapshotVersions = mavenMetadataLocal.versioning.snapshotVersions?.snapshotVersions
        assertNotNull(snapshotVersions, "${mavenMetadataLocalPath.fileName} doesn't define any snapshotVersion")
        assertTrue(
            snapshotVersions.isNotEmpty(),
            "${mavenMetadataLocalPath.fileName} doesn't define any snapshotVersion"
        )

        val files = mavenMetadataLocalPath.parent.listDirectoryEntries().associateBy { it.fileName.toString() }

        val snapshotVersionsUpdated = mutableListOf<SnapshotVersion>()
        snapshotVersions.forEach { snapshotVersion ->
            val pattern = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            val file = files.entries.single { it.key.substringAfterLast(".") == snapshotVersion.extension }
            val lastModified = if (valid)
                LocalDateTime.ofInstant(file.value.getLastModifiedTime().toInstant(), ZoneId.of("UTC")).format(pattern)
            else
                "19991111010155" // random incorrect local time
            snapshotVersionsUpdated.add(snapshotVersion.copy(updated = lastModified))
        }

        val mavenMetadataLocalUpdated = mavenMetadataLocal.copy(
            versioning = mavenMetadataLocal.versioning.copy(
                snapshotVersions = mavenMetadataLocal.versioning.snapshotVersions.copy(
                    snapshotVersions = snapshotVersionsUpdated
                )
            )
        )

        try {
            mavenMetadataLocalPath.writeText(mavenMetadataLocalUpdated.serialize())
        } catch (e: AmperDependencyResolutionException) {
            fail("Unexpected exception on serializing maven-metadata", e)
        }
    }

    private fun initEtalonMavenLocalStorage(cacheRoot: Path): LocalRepository {
        // Installing maven local repository at the custom location
        cacheRoot.createDirectories()

        val mavenLocal = MavenLocalRepository(cacheRoot)

        initEtalonLocalStorage(cacheRoot, mavenLocal)
        return mavenLocal
    }

    private fun initEtalonLocalStorage(localStoragePath: Path, localStorage: LocalRepository) {
        val atomicfuCoordinates = "org.jetbrains.kotlinx:atomicfu-jvm:0.23.2"

        // Initialize resolution context
        val context = context(ResolutionScope.COMPILE, cacheBuilder = {
            amperCache = localStoragePath.resolve(".amper")
            localRepository = localStorage
            readOnlyExternalRepositories = emptyList()
        })

        val root = DependencyNodeHolder("root", listOf(atomicfuCoordinates.toMavenNode(context)), context)

        runBlocking {
            doTest(
                root,
                expected = """
                root
                ╰─── org.jetbrains.kotlinx:atomicfu-jvm:0.23.2
                """.trimIndent()
            )

            downloadAndAssertFiles(listOf("atomicfu-jvm-0.23.2.jar"), root)
        }
    }

    private fun checkLocalRepositoryUsage(
        testInfo: TestInfo,
        mavenCoordinates: MavenCoordinates,
        testCacheRoot: Path,
        verifyMessages: Boolean = true,
        filesThatShouldNotBeDownloaded: List<String> = emptyList(),
        filesThatMustBeDownloaded: List<String> = emptyList(),
        repositories: List<Repository> = listOf(REDIRECTOR_MAVEN_CENTRAL),
        updateLocalRepository: (LocalRepository) -> Unit = {},
        initLocalRepository: (Path) -> LocalRepository = { cacheRoot -> initEtalonMavenLocalStorage(cacheRoot) }
    ): DependencyNode {
        val urlPrefix =
            "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/${mavenCoordinates.urlFolderPath}"

        // Installing maven local repository at the custom location
        val localExternalRepository = initLocalRepository(testCacheRoot)

        // Initialize resolution context
        val cacheBuilder = withLocalRepository(testCacheRoot, localExternalRepository)
        val cache = FileCacheBuilder(cacheBuilder).build()
        val context = context(
            ResolutionScope.COMPILE,
            repositories = repositories,
            cacheBuilder = cacheBuilder
        )
        val httpClient = createTestHttpClient(
            filesThatShouldNotBeDownloaded.map { "$urlPrefix/$it" }
        )
        context.resolutionCache.computeIfAbsent(httpClientKey) { httpClient }

        val nodeInCompileContext = mavenCoordinates.toMavenNode(context)

        val root = DependencyNodeHolder("root", listOf(nodeInCompileContext), context)

        updateLocalRepository(localExternalRepository)

        assertTrue(
            !(cache.localRepository as MavenLocalRepository).repository.exists()
                    || cache.localRepository.repository.listDirectoryEntries().isEmpty(),
            "Local repository should not contain artifacts resolved from mavenLocal"
        )

        runBlocking {
            doTestByFile(
                testInfo,
                root,
                verifyMessages = verifyMessages
            )
        }

        if (localExternalRepository is MavenLocalRepository && repositories.contains(MavenLocal)) {
            // Artifact resolved from mavenLocal is not copied to DR local cache
            assertFalse(
                cache.localRepository.repository.resolve(mavenCoordinates.urlPath)
                    .exists(),
                "Local repository should not contain library $mavenCoordinates"
            )
            assertTrue(localExternalRepository.repository.resolve(mavenCoordinates.urlPath)
                .exists(),
                "Maven local repository SHOULD contain library $mavenCoordinates"
            )
        } else {
            assertTrue(
                cache.localRepository.repository.resolve(mavenCoordinates.urlPath)
                    .exists(),
                "Local repository SHOULD contain library $mavenCoordinates"
            )
        }

        runBlocking {
            downloadAndAssertFiles(testInfo, root)
        }

        val notDownloaded =
            filesThatMustBeDownloaded.filter { fileName -> httpClient.processedUrls.none { it.path.toString().endsWith(fileName) } }
        assertTrue(
            notDownloaded.isEmpty(),
            "The following artifacts should have been downloaded, but they were not: ${notDownloaded.joinToString("\n")}"
        )

        return root
    }

    private val MavenCoordinates.urlFolderPath
        get() = "${groupId.replace(".", "/")}/$artifactId/$version"

    private val MavenCoordinates.urlPath
        get() = "$urlFolderPath/$fileName"

    private val MavenCoordinates.fileName
        get() = "$artifactId-$version.jar"

    /**
     * Create Amper like configuration of repositories.
     * Maven local repository ir reused.
     */
    fun withLocalRepository(
        cacheRoot: Path,
        localReadOnlyExternalRepository: LocalRepository? = null
    ): FileCacheBuilder.() -> Unit = {
        amperCache = cacheRoot
        localRepository = MavenLocalRepository(cacheRoot.resolve(".m2.cache.test"))
        readOnlyExternalRepositories = localReadOnlyExternalRepository?.let { listOf(it) } ?: emptyList()
    }

    private fun createTestHttpClient(urlThatShouldNotBeDownloaded: List<String>): TestHttpClient {
        val client = HttpClient.newBuilder()
            .version(Version.HTTP_1_1)
            .followRedirects(Redirect.NORMAL)
            .sslContext(SSLContext.getDefault())
            .connectTimeout(Duration.ofSeconds(20))
            .build()
        return TestHttpClient(client, urlThatShouldNotBeDownloaded)
    }

    class TestHttpClient(val client: HttpClient, private val failOnUrls: List<String>) : HttpClient() {

        val processedUrls: MutableList<URI> = mutableListOf()

        override fun cookieHandler(): Optional<CookieHandler?>? = client.cookieHandler()
        override fun connectTimeout(): Optional<Duration?>? = client.connectTimeout()
        override fun followRedirects(): HttpClient.Redirect? = client.followRedirects()
        override fun proxy(): Optional<ProxySelector?>? = client.proxy()
        override fun sslContext(): SSLContext? = client.sslContext()
        override fun sslParameters(): SSLParameters? = client.sslParameters()
        override fun authenticator(): Optional<Authenticator?>? = client.authenticator()
        override fun version(): HttpClient.Version? = client.version()
        override fun executor(): Optional<Executor?>? = client.executor()
        override fun <T : Any?> send(p0: HttpRequest?, p1: HttpResponse.BodyHandler<T?>?) =
            withUrlsCheck(p0) { client.send(p0, p1) }

        override fun <T : Any?> sendAsync(p0: HttpRequest?, p1: HttpResponse.BodyHandler<T?>?) =
            withUrlsCheck(p0) { client.sendAsync(p0, p1) }

        override fun <T : Any?> sendAsync(
            p0: HttpRequest?,
            p1: HttpResponse.BodyHandler<T?>?,
            p2: HttpResponse.PushPromiseHandler<T?>?
        ) = withUrlsCheck(p0) { client.sendAsync(p0, p1, p2) }

        private fun <T> withUrlsCheck(request: HttpRequest?, block: () -> T): T {
            request?.let {
                if (failOnUrls.any { request.uri() == URI.create(it) }) {
                    fail("Unexpected request to ${request.uri()}")
                }

                processedUrls.add(request.uri())
            }

            return block()
        }
    }

    /**
     * Temporarily resets custom maven settings of the local machine to control the test configuration completely.
     */
    private fun clearLocalM2MachineOverrides(systemProperties: SystemProperties, environmentVariables: EnvironmentVariables) {
        systemProperties.set("maven.repo.local", "")
        systemProperties.set("user.home", Path("nothing-to-see-here").absolutePathString())
        environmentVariables.set("M2_HOME", "")
    }
}
