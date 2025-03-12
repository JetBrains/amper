/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.util

import org.apache.commons.io.output.TeeOutputStream
import org.gradle.tooling.GradleConnector
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.gradle.MockModelHandle
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.test.asserter


abstract class TestBase {
    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUpGradleSettings() {
        setUpGradleProjectDir(tempDir)
    }
}

// Need to be inlined, since looks for trace.
@Suppress("NOTHING_TO_INLINE")
internal inline fun TestBase.doTest(model: MockModelHandle) {
    val runResult = runGradleWithModel(model)
    val extracted = runResult.extractSourceInfoOutput()
    assertEqualsWithCurrentTestResource(extracted)
}

fun TestBase.runGradleWithModel(model: MockModelHandle): String {
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    GradleConnector.newConnector()
        // we use this instead of useGradleVersion() so that our tests benefit from the cache redirector and avoid timeouts
        .useDistribution(URI("https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-8.6-bin.zip"))
        .useGradleUserHomeDir(Dirs.sharedGradleHome.toFile())
        .forProjectDirectory(tempDir.toFile())
        .connect()
        .use { projectConnection ->
            projectConnection
                .newBuild()
                .withArguments(printKotlinSourcesTask, "--stacktrace")
                .withMockModel(model)
                .setStandardError(TeeOutputStream(System.err, stderr))
                .setStandardOutput(TeeOutputStream(System.out, stdout))
                .run()
        }
    val output =
        (stdout.toByteArray().decodeToString() + "\n" + stderr.toByteArray().decodeToString()).replace("\r", "")
    return output
}

fun setUpGradleProjectDir(root: Path) {
    val settingsFile = root / "settings.gradle.kts"
    settingsFile.createFile()

    val plugins = """
        pluginManagement {
            repositories {
                mavenLocal()
                mavenCentral()
                google()
                gradlePluginPortal()
                maven("https://www.jetbrains.com/intellij-repository/releases")
                maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
            }
        }

        plugins {
            id("org.jetbrains.amper.settings.plugin").version("${AmperBuild.mavenVersion}")
        }
    """.trimIndent()
    val settingsFileContent = if (withDebug) plugins else """
            import org.jetbrains.amper.gradle.util.PrintKotlinSpecificInfo
            $plugins
            // Apply also plugin to print kotlin specific info.
            plugins.apply(PrintKotlinSpecificInfo::class.java)
        """
    settingsFile.writeText(settingsFileContent.trimIndent())
}

// Need to be inlined, since looks for trace.
internal inline val currentTestName: String
    get() = run {
        val currentTrace = Thread.currentThread().stackTrace
        println(currentTrace.map { it.methodName })
        currentTrace[1].let { "${it.decapitalizedSimpleName}/${it.methodName}" }
    }

val StackTraceElement.decapitalizedSimpleName: String
    get() = className.substringAfterLast(".").replaceFirstChar { it.lowercase() }

// Need to be inlined, since looks for trace.
@Suppress("NOTHING_TO_INLINE")
internal inline fun TestBase.assertEqualsWithCurrentTestResource(actual: String) =
    assertEqualsWithResource("testAssets/$currentTestName", actual)

fun TestBase.assertEqualsWithResource(expectedResourceName: String, actual: String) =
    Thread.currentThread().contextClassLoader
        .getResource(expectedResourceName)
        ?.readText()
        ?.replace("\$TEMP_DIR_NAME", tempDir.name)
        ?.let {
            assertEqualsIgnoreLineSeparator(it, actual)
        }
        ?: error("No resource $expectedResourceName!")

fun assertEqualsIgnoreLineSeparator(expected: String, checkText: String, message: String? = null) {
    if (expected.replaceLineSeparators() != checkText.replaceLineSeparators()) {
        asserter.assertEquals(message, expected, checkText)
    }
}

fun String.replaceLineSeparators() = replace("\n", "").replace("\r", "")
