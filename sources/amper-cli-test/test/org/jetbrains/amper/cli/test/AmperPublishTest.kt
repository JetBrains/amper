/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import com.sun.net.httpserver.BasicAuthenticator
import org.jetbrains.amper.cli.test.utils.assertContainsRelativeFiles
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.test.server.withFileServer
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperPublishTest : AmperCliTestBase() {

    private fun createTempMavenLocalDir(): Path = tempRoot.resolve(".m2.test").also { it.createDirectories() }

    private fun mavenRepoLocalJvmArg(mavenLocalForTest: Path) =
        "-Dmaven.repo.local=\"${mavenLocalForTest.absolutePathString()}\""

    @Test
    fun `publish to maven local (jvm single-module)`() = runSlowTest {
        val mavenLocalForTest = createTempMavenLocalDir()
        val groupDir = mavenLocalForTest.resolve("amper/test/jvm-publish")

        runCli(
            projectRoot = testProject("jvm-publish"),
            "publish", "mavenLocal",
            amperJvmArgs = listOf(mavenRepoLocalJvmArg(mavenLocalForTest)),
        )

        groupDir.assertContainsRelativeFiles(
            "artifactName/2.2/_remote.repositories",
            "artifactName/2.2/artifactName-2.2-sources.jar",
            "artifactName/2.2/artifactName-2.2.jar",
            "artifactName/2.2/artifactName-2.2.pom",
            "artifactName/maven-metadata-local.xml",
        )

        val pom = groupDir.resolve("artifactName/2.2/artifactName-2.2.pom")
        assertEquals("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <modelVersion>4.0.0</modelVersion>
              <groupId>amper.test.jvm-publish</groupId>
              <artifactId>artifactName</artifactId>
              <version>2.2</version>
              <name>jvm-publish</name>
              <dependencies>
                <dependency>
                  <groupId>org.jetbrains.kotlin</groupId>
                  <artifactId>kotlin-stdlib</artifactId>
                  <version>${UsedVersions.kotlinVersion}</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>io.ktor</groupId>
                  <artifactId>ktor-client-core-jvm</artifactId>
                  <version>2.3.9</version>
                  <scope>compile</scope>
                </dependency>
                <dependency>
                  <groupId>io.ktor</groupId>
                  <artifactId>ktor-client-java-jvm</artifactId>
                  <version>2.3.9</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlinx</groupId>
                  <artifactId>kotlinx-coroutines-core-jvm</artifactId>
                  <version>1.7.1</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlinx</groupId>
                  <artifactId>kotlinx-serialization-core-jvm</artifactId>
                  <version>1.6.3</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlinx</groupId>
                  <artifactId>kotlinx-serialization-json-jvm</artifactId>
                  <version>1.6.3</version>
                  <scope>provided</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlinx</groupId>
                  <artifactId>kotlinx-serialization-cbor-jvm</artifactId>
                  <version>1.6.3</version>
                  <scope>compile</scope>
                </dependency>
              </dependencies>
            </project>
        """.trimIndent(), pom.readText().trim())
    }

    @Test
    fun `publish to maven local (jvm multi-module)`() = runSlowTest {
        val mavenLocalForTest = createTempMavenLocalDir()
        val groupDir = mavenLocalForTest.resolve("amper/test/jvm-publish-multimodule")

        runCli(
            projectRoot = testProject("jvm-publish-multimodule"),
            "publish", "mavenLocal", "--modules=main-lib",
            amperJvmArgs = listOf(mavenRepoLocalJvmArg(mavenLocalForTest)),
        )

        // note that publishing of main-lib module triggers all other modules (by design)
        groupDir.assertContainsRelativeFiles(
            "jvm-lib/1.2.3/_remote.repositories",
            "jvm-lib/1.2.3/jvm-lib-1.2.3-sources.jar",
            "jvm-lib/1.2.3/jvm-lib-1.2.3.jar",
            "jvm-lib/1.2.3/jvm-lib-1.2.3.pom",
            "jvm-lib/maven-metadata-local.xml",
            "kmp-lib-jvm/1.2.3/_remote.repositories",
            "kmp-lib-jvm/1.2.3/kmp-lib-jvm-1.2.3-sources.jar",
            "kmp-lib-jvm/1.2.3/kmp-lib-jvm-1.2.3.jar",
            "kmp-lib-jvm/1.2.3/kmp-lib-jvm-1.2.3.pom",
            "kmp-lib-jvm/maven-metadata-local.xml",
            "main-lib/1.2.3/_remote.repositories",
            "main-lib/1.2.3/main-lib-1.2.3-sources.jar",
            "main-lib/1.2.3/main-lib-1.2.3.jar",
            "main-lib/1.2.3/main-lib-1.2.3.pom",
            "main-lib/maven-metadata-local.xml",
        )

        val pom = groupDir / "main-lib/1.2.3/main-lib-1.2.3.pom"
        assertEquals(expected = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <modelVersion>4.0.0</modelVersion>
              <groupId>amper.test.jvm-publish-multimodule</groupId>
              <artifactId>main-lib</artifactId>
              <version>1.2.3</version>
              <name>main-lib</name>
              <dependencies>
                <dependency>
                  <groupId>org.jetbrains.kotlin</groupId>
                  <artifactId>kotlin-stdlib</artifactId>
                  <version>${UsedVersions.kotlinVersion}</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>amper.test.jvm-publish-multimodule</groupId>
                  <artifactId>jvm-lib</artifactId>
                  <version>1.2.3</version>
                  <scope>compile</scope>
                </dependency>
                <dependency>
                  <groupId>amper.test.jvm-publish-multimodule</groupId>
                  <artifactId>kmp-lib-jvm</artifactId>
                  <version>1.2.3</version>
                  <scope>runtime</scope>
                </dependency>
              </dependencies>
            </project>
        """.trimIndent(), pom.readText().trim())
    }

    @Test
    fun `publish to remote repo without auth`() = runSlowTest {
        val www = tempRoot.resolve("www-root").also { it.createDirectories() }

        withFileServer(www) { baseUrl ->
            publishJvmProject("2.2", baseUrl)
        }

        www.resolve("amper/test/jvm-publish").assertContainsRelativeFiles(
            "artifactName/2.2/artifactName-2.2-sources.jar",
            "artifactName/2.2/artifactName-2.2-sources.jar.md5",
            "artifactName/2.2/artifactName-2.2-sources.jar.sha1",
            "artifactName/2.2/artifactName-2.2-sources.jar.sha256",
            "artifactName/2.2/artifactName-2.2-sources.jar.sha512",
            "artifactName/2.2/artifactName-2.2.jar",
            "artifactName/2.2/artifactName-2.2.jar.md5",
            "artifactName/2.2/artifactName-2.2.jar.sha1",
            "artifactName/2.2/artifactName-2.2.jar.sha256",
            "artifactName/2.2/artifactName-2.2.jar.sha512",
            "artifactName/2.2/artifactName-2.2.pom",
            "artifactName/2.2/artifactName-2.2.pom.md5",
            "artifactName/2.2/artifactName-2.2.pom.sha1",
            "artifactName/2.2/artifactName-2.2.pom.sha256",
            "artifactName/2.2/artifactName-2.2.pom.sha512",
            "artifactName/maven-metadata.xml",
            "artifactName/maven-metadata.xml.md5",
            "artifactName/maven-metadata.xml.sha1",
            "artifactName/maven-metadata.xml.sha256",
            "artifactName/maven-metadata.xml.sha512",
        )
    }

    @Test
    fun `publish to remote repo with password auth`() = runSlowTest {
        val www = tempRoot.resolve("www-root").also { it.createDirectories() }

        withFileServer(www, authenticator = createAuthenticator()) { baseUrl ->
            publishJvmProject("2.2", baseUrl)
        }

        www.resolve("amper/test/jvm-publish").assertContainsRelativeFiles(
            "artifactName/2.2/artifactName-2.2-sources.jar",
            "artifactName/2.2/artifactName-2.2-sources.jar.md5",
            "artifactName/2.2/artifactName-2.2-sources.jar.sha1",
            "artifactName/2.2/artifactName-2.2-sources.jar.sha256",
            "artifactName/2.2/artifactName-2.2-sources.jar.sha512",
            "artifactName/2.2/artifactName-2.2.jar",
            "artifactName/2.2/artifactName-2.2.jar.md5",
            "artifactName/2.2/artifactName-2.2.jar.sha1",
            "artifactName/2.2/artifactName-2.2.jar.sha256",
            "artifactName/2.2/artifactName-2.2.jar.sha512",
            "artifactName/2.2/artifactName-2.2.pom",
            "artifactName/2.2/artifactName-2.2.pom.md5",
            "artifactName/2.2/artifactName-2.2.pom.sha1",
            "artifactName/2.2/artifactName-2.2.pom.sha256",
            "artifactName/2.2/artifactName-2.2.pom.sha512",
            "artifactName/maven-metadata.xml",
            "artifactName/maven-metadata.xml.md5",
            "artifactName/maven-metadata.xml.sha1",
            "artifactName/maven-metadata.xml.sha256",
            "artifactName/maven-metadata.xml.sha512",
        )
    }

    @Test
    fun `jvm publish adds to maven-metadata xml`() = runSlowTest {
        val www = tempRoot.resolve("www-root").also { it.createDirectories() }

        withFileServer(www, authenticator = createAuthenticator()) { baseUrl ->
            publishJvmProject("2.2", baseUrl)
            publishJvmProject("2.3", baseUrl)
        }

        val mavenMetadataXml = www.resolve("amper/test/jvm-publish/artifactName/maven-metadata.xml")
        assertMetadataWithTimestampEquals("""
            <?xml version="1.0" encoding="UTF-8"?>
            <metadata>
              <groupId>amper.test.jvm-publish</groupId>
              <artifactId>artifactName</artifactId>
              <versioning>
                <release>2.3</release>
                <versions>
                  <version>2.2</version>
                  <version>2.3</version>
                </versions>
                <lastUpdated>TIMESTAMP</lastUpdated>
              </versioning>
            </metadata>
        """.trimIndent(), mavenMetadataXml)
    }

    @Test
    fun `jvm publish handles snapshot versioning`() = runSlowTest {
        val www = tempRoot.resolve("www-root").also { it.createDirectories() }

        withFileServer(www, authenticator = createAuthenticator()) { baseUrl ->
            // For some reason, in this test, some logging fails at the end of the run.
            // It might be due to some maven cleanup happening in a shutdown hook after logging itself is shutdown.
            publishJvmProject("1.0", baseUrl)
            publishJvmProject("2.0-SNAPSHOT", baseUrl)
            publishJvmProject("2.0-SNAPSHOT", baseUrl)
        }

        assertMetadataWithTimestampEquals("""
            <?xml version="1.0" encoding="UTF-8"?>
            <metadata>
              <groupId>amper.test.jvm-publish</groupId>
              <artifactId>artifactName</artifactId>
              <versioning>
                <release>1.0</release>
                <versions>
                  <version>1.0</version>
                  <version>2.0-SNAPSHOT</version>
                </versions>
                <lastUpdated>TIMESTAMP</lastUpdated>
              </versioning>
            </metadata>
        """.trimIndent(), www.resolve("amper/test/jvm-publish/artifactName/maven-metadata.xml"))

        val lastVersion = www.resolve("amper/test/jvm-publish/artifactName/2.0-SNAPSHOT").listDirectoryEntries()
            .map { it.name }
            .sorted()
            .last { it.startsWith("artifactName-") && it.endsWith(".jar") }
            .removePrefix("artifactName-")
            .removeSuffix(".jar")

        val metadataXml = www.resolve("amper/test/jvm-publish/artifactName/2.0-SNAPSHOT/maven-metadata.xml")
        assertMetadataWithTimestampEquals("""
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <groupId>amper.test.jvm-publish</groupId>
                  <artifactId>artifactName</artifactId>
                  <versioning>
                    <lastUpdated>TIMESTAMP</lastUpdated>
                    <snapshot>
                      <timestamp>TIMESTAMP</timestamp>
                      <buildNumber>2</buildNumber>
                    </snapshot>
                    <snapshotVersions>
                      <snapshotVersion>
                        <extension>jar</extension>
                        <value>$lastVersion</value>
                        <updated>TIMESTAMP</updated>
                      </snapshotVersion>
                      <snapshotVersion>
                        <classifier>sources</classifier>
                        <extension>jar</extension>
                        <value>$lastVersion</value>
                        <updated>TIMESTAMP</updated>
                      </snapshotVersion>
                      <snapshotVersion>
                        <extension>pom</extension>
                        <value>$lastVersion</value>
                        <updated>TIMESTAMP</updated>
                      </snapshotVersion>
                    </snapshotVersions>
                  </versioning>
                  <version>2.0-SNAPSHOT</version>
                </metadata>
            """.trimIndent(), metadataXml)
    }

    private suspend fun publishJvmProject(version: String, repoUrl: String) {
        runCli(
            projectRoot = testProject("jvm-publish"),
            "publish", "repoId",
            copyToTempDir = true,
            modifyTempProjectBeforeRun = { root ->
                val moduleYaml = root.resolve("module.yaml")
                moduleYaml.writeText(moduleYaml.readText().replace("REPO_URL", repoUrl).replace("2.2", version))
            },
            amperJvmArgs = listOf(mavenRepoLocalJvmArg(createTempMavenLocalDir()))
        )
    }

    private fun createAuthenticator(
        expectedUser: String = "http-user",
        expectedPassword: String = "http-password",
    ): BasicAuthenticator = object : BasicAuthenticator("www-realm") {
        override fun checkCredentials(username: String, password: String): Boolean =
            username == expectedUser && password == expectedPassword
    }

    private fun assertMetadataWithTimestampEquals(expected: String, actualMetadataFile: Path) {
        assertEquals(
            expected.trim(),
            actualMetadataFile.readText().trim()
                .replace(Regex("<lastUpdated>\\d+</lastUpdated>"), "<lastUpdated>TIMESTAMP</lastUpdated>")
                .replace(Regex("<updated>\\d+</updated>"), "<updated>TIMESTAMP</updated>")
                .replace(Regex("<timestamp>[\\d.]+</timestamp>"), "<timestamp>TIMESTAMP</timestamp>"),
        )
    }
}
