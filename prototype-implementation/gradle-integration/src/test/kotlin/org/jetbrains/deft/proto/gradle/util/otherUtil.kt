package org.jetbrains.deft.proto.gradle.util

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.InvalidPluginMetadataException
import org.jetbrains.deft.proto.gradle.MockModelHandle
import org.junit.jupiter.api.Assertions
import java.io.File
import java.util.*


interface WithTempDir {
    var tempDir: File
}

fun WithTempDir.runGradleWithModel(model: MockModelHandle): BuildResult = GradleRunner.create()
    .withArguments(printKotlinSourcesTask, "--stacktrace")
    .withPluginClasspath()
    .withProjectDir(tempDir)
    .withMockModel(model)
    .withDebug(withDebug)
    .build()

fun setUpGradleProjectDir(root: File) {
    val settingsFile = root.resolve("settings.gradle.kts")
    settingsFile.createNewFile()
    val settingsFileContent = if (withDebug) """
            plugins {
                id("org.jetbrains.deft.proto.settings.plugin")
            }
        """
    else """
            import org.jetbrains.deft.proto.gradle.util.PrintKotlinSpecificInfo
            plugins {
                id("org.jetbrains.deft.proto.settings.plugin")
            }
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
        className.substringAfterLast(".").replaceFirstChar { it.lowercase(Locale.getDefault()) }

// Need to be inlined, since looks for trace.
@Suppress("NOTHING_TO_INLINE")
internal inline fun WithTempDir.assertEqualsWithCurrentTestResource(actual: String) =
        assertEqualsWithResource("testAssets/$currentTestName", actual)

fun WithTempDir.assertEqualsWithResource(expectedResourceName: String, actual: String) =
        Thread.currentThread().contextClassLoader
                .getResource(expectedResourceName)
                ?.readText()
                ?.replace("\$TEMP_DIR_NAME", tempDir.name)
                ?.let { Assertions.assertEquals(it, actual) }
                ?: error("No resource $expectedResourceName!")