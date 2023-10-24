package org.jetbrains.amper.gradle.util

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.amper.gradle.MockModelHandle
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.*


abstract class TestBase {

    @field:TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUpGradleSettings() = setUpGradleProjectDir(tempDir)

}

// Need to be inlined, since looks for trace.
@Suppress("NOTHING_TO_INLINE")
internal inline fun TestBase.doTest(model: MockModelHandle) {
    val runResult = runGradleWithModel(model)
    val printKotlinInfo = runResult.task(":$printKotlinSourcesTask")
    Assertions.assertEquals(TaskOutcome.SUCCESS, printKotlinInfo?.outcome)
    val extracted = runResult.output.extractSourceInfoOutput()
    assertEqualsWithCurrentTestResource(extracted)
}

fun TestBase.runGradleWithModel(model: MockModelHandle): BuildResult = GradleRunner.create()
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
                id("org.jetbrains.amper.settings.plugin")
            }
        """
    else """
            import org.jetbrains.amper.gradle.util.PrintKotlinSpecificInfo
            plugins {
                id("org.jetbrains.amper.settings.plugin")
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
                Assertions.assertEquals(it, actual)
            }
        }
        ?: error("No resource $expectedResourceName!")