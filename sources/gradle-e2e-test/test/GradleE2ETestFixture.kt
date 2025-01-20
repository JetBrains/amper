/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.coroutines.runBlocking
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnector
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.android.AndroidTools
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.CopyActionResult
import kotlin.io.path.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import kotlin.test.fail


open class GradleE2ETestFixture(val pathToProjects: String, val runWithPluginClasspath: Boolean = true) {

    @Suppress("unused") // JUnit5 extension.
    @field:RegisterExtension
    val daemonManagerExtension = GradleDaemonManager

    /**
     * Daemon, used to run this test.
     */

    lateinit var gradleRunner: GradleConnector

    internal fun test(
        projectName: String,
        vararg buildArguments: String,
        expectOutputToHave: String,
        shouldSucceed: Boolean = true,
        checkForWarnings: Boolean = true,
        gradleVersion: String = "8.6",
        additionalEnv: Map<String, String> = emptyMap(),
        additionalCheck: TestResultAsserter.() -> Unit = {},
    ) {
        test(
            projectName,
            buildArguments = buildArguments,
            expectOutputToHave = listOf(expectOutputToHave),
            shouldSucceed = shouldSucceed,
            checkForWarnings = checkForWarnings,
            gradleVersion = gradleVersion,
            additionalEnv = additionalEnv,
            additionalCheck = additionalCheck,
        )
    }

    internal fun test(
        projectName: String,
        vararg buildArguments: String,
        expectOutputToHave: Collection<String> = emptyList(),
        shouldSucceed: Boolean = true,
        checkForWarnings: Boolean = true,
        gradleVersion: String = "8.6",
        additionalEnv: Map<String, String> = emptyMap(),
        additionalCheck: TestResultAsserter.() -> Unit = {},
    ) {
        val tempDir = prepareTempDirWithProject(projectName, runWithPluginClasspath)
        val newEnv = System.getenv().toMutableMap().apply { putAll(additionalEnv) }
        newEnv["ANDROID_HOME"] = runBlocking { AndroidTools.getOrInstallForTests().androidHome.pathString }
        val runner = gradleRunner
        val projectConnector = runner
            // we use this instead of useGradleVersion() so that our tests benefit from the cache redirector and avoid timeouts
            .useDistribution(URI("https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"))
            .forProjectDirectory(tempDir.toFile())
            .connect()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        try {
            projectConnector.newBuild()
                .setEnvironmentVariables(newEnv)
                // --no-build-cache: do not get a build result from shared Gradle cache
                .withArguments(*buildArguments, "--stacktrace", "--no-build-cache")
                .setStandardOutput(TeeOutputStream(System.out, stdout))
                .setStandardError(TeeOutputStream(System.err, stderr))
//                .addJvmArguments("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
                .run()
        } catch (t: BuildException) {
            if (shouldSucceed) {
                throw t
            } else {
                // skip, the error will be checked by expectOutputToHave
            }
        } finally {
            projectConnector.close()
        }
        val output = (stdout.toByteArray().decodeToString() + "\n" + stderr.toByteArray().decodeToString()).replace("\r", "")

        val missingStrings = expectOutputToHave.filter { !output.contains(it) }
        assertTrue(missingStrings.isEmpty(),
            "The following strings are not found in the build output:\n" +
                    missingStrings.joinToString("\n") { "\t" + it } +
                    "\nOutput:\n$output")

        if (checkForWarnings) output.checkForWarnings()

        val testResultAsserter = TestResultAsserter(tempDir, output)
        additionalCheck(testResultAsserter)
        assertTrue(
            actual = testResultAsserter.failedAssertions.isEmpty(),
            message = "The following assertions failed:\n${testResultAsserter.failedAssertions.joinToString("\n") { " - $it" }}\n\nOutput:\n$output"
        )
    }

    private fun prepareTempDirWithProject(projectName: String, runWithPluginClasspath: Boolean): Path {
        val implementationDir = Path("../../sources").toAbsolutePath()
        val originalDir = Path("${pathToProjects}/$projectName")

        assertTrue(implementationDir.exists(), "Amper plugin project not found at $implementationDir")
        assertTrue(originalDir.exists(), "Test project not found at $originalDir")

        // prepare data
        val tempDir = createTempFile(Dirs.tempDir, "test-", "-$projectName")
        tempDir.deleteExisting()
        GradleDaemonManager.deleteFileOrDirectoryOnExit(tempDir)

        val followLinks = false
        val ignore = setOf(".gradle", "build", "caches")
        originalDir.copyToRecursively(tempDir, followLinks = followLinks) { src, dst ->
            if (src.name in ignore) CopyActionResult.SKIP_SUBTREE
            else src.copyToIgnoringExistingDirectory(dst, followLinks = followLinks)
        }

        val gradleFile = tempDir.resolve("settings.gradle.kts")
        assertTrue(gradleFile.exists(), "file not found: $gradleFile")

        if (runWithPluginClasspath) {
            // we don't want to replace the entire settings.gradle.kts because its contents may be part of the test
            gradleFile.writeLines(gradleFile.readLines().filter { "<REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>" !in it })

            check(!gradleFile.readText().contains("mavenLocal", ignoreCase = true)) {
                "Gradle files in testData are not supposed to reference mavenLocal by default: " +
                        "$gradleFile\n${gradleFile.readText().prependIndent("> ")}"
            }

            // Add actual plugin to classpath, it's published to mavenLocal before running tests
            gradleFile.writeText(gradleFile.readText().replace(
                "mavenCentral()",
                """
                    mavenCentral()
                    mavenLocal()
                    maven("https://www.jetbrains.com/intellij-repository/releases")
                    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
                """.trimIndent()
            ))
            check(gradleFile.readText().contains("mavenLocal")) {
                "Gradle file must have 'mavenLocal' after replacement: $gradleFile\n${gradleFile.readText().prependIndent("> ")}"
            }

            // Add Amper plugin version
            gradleFile.writeText(gradleFile.readText().replace(
                Regex("id\\(\"org.jetbrains.amper.settings.plugin\"\\)[. ]version\\(\".+\"\\)", RegexOption.MULTILINE),
                "id(\"org.jetbrains.amper.settings.plugin\")"
            ))

            gradleFile.writeText(gradleFile.readText().replace(
                "id(\"org.jetbrains.amper.settings.plugin\")",
                "id(\"org.jetbrains.amper.settings.plugin\") version(\"${AmperBuild.mavenVersion}\")"
            ))
            check(gradleFile.readText().contains("version(")) {
                "Gradle file must have 'version(' after replacement: $gradleFile\n${gradleFile.readText().prependIndent("> ")}"
            }
        }

        // These errors can be tricky to figure out
        if ("includeBuild(\"." in gradleFile.readText()) {
            fail("Example project $projectName has a relative includeBuild() call, but it's run within Amper tests " +
                    "from a moved directory. Add a comment '<REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>' on the same " +
                    "line if this included build is for Amper itself (will be removed if Amper is on the classpath).")
        }
        return tempDir
    }
}

class TestResultAsserter(
    val projectDir: Path,
    private val outputText: String,
) {
    val failedAssertions = mutableListOf<String>()

    fun assertOutputContains(text: String) {
        if (text !in outputText) {
            failedAssertions.add("the following text should appear in the output, but it didn't: '$text'")
        }
    }

    fun assertNotInOutput(text: String) {
        if (text in outputText) {
            failedAssertions.add("the following text should not appear in the output, but it did: '$text'")
        }
    }

    fun assertTaskSucceeded(taskPath: String) {
        if ("> Task $taskPath" !in outputText) {
            failedAssertions.add("task '$taskPath' should have succeeded, but was not run at all")
        } else if ("> Task $taskPath FAILED" in outputText) {
            failedAssertions.add("task '$taskPath' should have succeeded, but failed")
        }
    }

    fun assertTaskFailed(taskPath: String) {
        if ("> Task $taskPath" !in outputText) {
            failedAssertions.add("task '$taskPath' should have failed, but was not run at all")
        } else if ("> Task $taskPath FAILED" !in outputText) {
            failedAssertions.add("task '$taskPath' should have failed, but succeeded")
        }
    }
}
