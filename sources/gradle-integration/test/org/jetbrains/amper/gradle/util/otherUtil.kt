/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.util

import org.apache.commons.io.output.TeeOutputStream
import org.gradle.tooling.GradleConnector
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.gradle.MockModelHandle
import org.jetbrains.amper.test.TestUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import kotlin.io.path.createDirectories
import kotlin.test.asserter


abstract class TestBase {

    @field:TempDir
    lateinit var tempDir: File

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

private val gradleHome by lazy {
    TestUtil.sharedTestCaches.resolve("gradleHome")
        .also { it.createDirectories() }
}

fun TestBase.runGradleWithModel(model: MockModelHandle): String {
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    GradleConnector.newConnector()
        // we use this instead of useGradleVersion() so that our tests benefit from the cache redirector and avoid timeouts
        .useDistribution(URI("https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-8.6-bin.zip"))
        .useGradleUserHomeDir(gradleHome.toFile())
        .forProjectDirectory(tempDir)
        .connect()
        .newBuild()
        .withArguments(printKotlinSourcesTask, "--stacktrace")
        .withMockModel(model)
        .setStandardError(TeeOutputStream(System.err, stderr))
        .setStandardOutput(TeeOutputStream(System.out, stdout))
        .run()
    val output = (stdout.toByteArray().decodeToString() + "\n" + stderr.toByteArray().decodeToString()).replace("\r", "")
    return output
}

fun setUpGradleProjectDir(root: File) {
    val settingsFile = root.resolve("settings.gradle.kts")
    settingsFile.createNewFile()

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
internal inline val currentTestName get(): String = run {
    val currentTrace = Thread.currentThread().stackTrace
    println(currentTrace.map { it.methodName })
    currentTrace[1].let { "${it.decapitalizedSimpleName}/${it.methodName}" }
}

val StackTraceElement.decapitalizedSimpleName get() =
    className.substringAfterLast(".").replaceFirstChar { it.lowercase() }

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
            if (fastReplace) {
                val toReplace = actual.replace(tempDir.name, "\$TEMP_DIR_NAME")
                val expectedFile = File(".").absoluteFile.resolve("testResources/$expectedResourceName")
                if (expectedFile.exists()) expectedFile.writeText(toReplace)
            } else {
                assertEqualsIgnoreLineSeparator(it, actual)
            }
        }
        ?: error("No resource $expectedResourceName!")

fun assertEqualsIgnoreLineSeparator(expected: String, checkText: String, message: String? = null) {
    if (expected.replaceLineSeparators() != checkText.replaceLineSeparators()) {
        asserter.assertEquals(message, expected, checkText)
    }
}

fun String.replaceLineSeparators() = replace("\n", "").replace("\r", "")