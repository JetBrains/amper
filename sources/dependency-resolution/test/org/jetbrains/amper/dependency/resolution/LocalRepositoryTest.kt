package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.io.File
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
import java.util.*
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalRepositoryTest: BaseDRTest() {

    @field:TempDir
    lateinit var temp: File

    private fun cacheRoot(): Path = temp.toPath().resolve(UUID.randomUUID().toString().padEnd(8, '0').substring(1..8))

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
            // Jar file has an incorrect checksums in maven local storage, => we ignore it
            // Checksums are not taken from maven local storage
            filesThatMustBeDownloaded = listOf(
                "atomicfu-jvm-0.23.2.jar",
                "atomicfu-jvm-0.23.2.pom.sha256",
                "atomicfu-jvm-0.23.2.module.sha256"
            ),
            // Corrupt JAR file stored in maven local storage, => it should be re-downloaded
            updateLocalRepository = { gradleLocalRepository ->
                val gradleLocalPath = (gradleLocalRepository as GradleLocalRepository).filesPath
                val atomicfuJarPath = gradleLocalPath.resolve("org.jetbrains.kotlinx/atomicfu-jvm/0.23.2/a4601dc42dceb031a586058e8356ff778a57dea0/atomicfu-jvm-0.23.2.jar")
                assertTrue(atomicfuJarPath.exists())
                atomicfuJarPath.appendText("No artifact from local storage have incorrect checksum and should be ignored")
            },
            initLocalRepository = { cacheRoot ->  initEtalonGradleLocalStorage(cacheRoot) }
        )
    }

    @Test
    fun `check maven local storage is ignored if primary storage misses artifact, but its checksum doesn't match`() {
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
                val atomicfuJarPath = mavenLocalPath.resolve("org/jetbrains/kotlinx/atomicfu-jvm/0.23.2/atomicfu-jvm-0.23.2.jar")
                assertTrue(atomicfuJarPath.exists())
                atomicfuJarPath.appendText("No artifact from local storage have incorrect checksum and should be ignored")
            },
            initLocalRepository = { cacheRoot ->  initEtalonMavenLocalStorage(cacheRoot) }
        )
    }

    private fun initEtalonGradleLocalStorage(cacheRoot: Path): LocalRepository {
        // Installing Gradle local repository at the custom location
        cacheRoot.createDirectories()

        val gradleLocal = GradleLocalRepository(cacheRoot)

        initEtalonLocalStorage(cacheRoot, gradleLocal)

        return gradleLocal
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
                expected = """root
        |\--- org.jetbrains.kotlinx:atomicfu-jvm:0.23.2""".trimMargin()
            )

            downloadAndAssertFiles("""atomicfu-jvm-0.23.2.jar""".trimMargin(), root)
        }
    }

    private fun checkLocalRepositoryUsage(
        filesThatShouldNotBeDownloaded: List<String> = emptyList(),
        filesThatMustBeDownloaded: List<String> = emptyList(),
        updateLocalRepository: (LocalRepository) -> Unit = {},
        initLocalRepository: (Path) -> LocalRepository = { cacheRoot -> initEtalonMavenLocalStorage(cacheRoot) }
    ) {
        val atomicfuCoordinates = "org.jetbrains.kotlinx:atomicfu-jvm:0.23.2"
        val urlPrefix = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/atomicfu-jvm/0.23.2"

        val cacheRoot = cacheRoot()

        // Installing maven local repository at the custom location
        val localExternalRepository = initLocalRepository(cacheRoot)

        // Initialize resolution context
        val cacheBuilder = withLocalRepository(cacheRoot, localExternalRepository)
        val cache = FileCacheBuilder(cacheBuilder).build()
        val context = context(ResolutionScope.COMPILE, cacheBuilder = cacheBuilder)

        val httpClient = createTestHttpClient(
            filesThatShouldNotBeDownloaded.map { "$urlPrefix/$it" }
        )
        context.resolutionCache.computeIfAbsent(httpClientKey) { httpClient }

        val nodeInCompileContext = atomicfuCoordinates.toMavenNode(context)

        val root = DependencyNodeHolder("root", listOf(nodeInCompileContext), context)

        updateLocalRepository(localExternalRepository)

        assertFalse(
            (cache.localRepository as MavenLocalRepository).repository.resolve("org/jetbrains/kotlinx/atomicfu-jvm").exists(),
            "Local repository should not contain atomicfu-jvm")

        runBlocking {
            doTest(
                root,
                expected = """root
        |\--- org.jetbrains.kotlinx:atomicfu-jvm:0.23.2""".trimMargin()
            )
        }

        assertTrue(
            (cache.localRepository as MavenLocalRepository).repository.resolve("org/jetbrains/kotlinx/atomicfu-jvm").exists(),
            "Local repository should not contain atomicfu-jvm")

        runBlocking {
            downloadAndAssertFiles("""atomicfu-jvm-0.23.2.jar""".trimMargin(), root)
        }

        val notDownloaded = filesThatMustBeDownloaded.filterNot { URI.create("$urlPrefix/$it") in httpClient.processedUrls }
        assertTrue(
            notDownloaded.isEmpty(),
            "The following artifacts should have been downloaded, but they were not: ${notDownloaded.joinToString("\n")}")
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
}