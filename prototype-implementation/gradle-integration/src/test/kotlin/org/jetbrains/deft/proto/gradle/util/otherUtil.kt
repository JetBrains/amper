package org.jetbrains.deft.proto.gradle.util

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.InvalidPluginMetadataException
import org.jetbrains.deft.proto.gradle.MockModelHandle
import java.io.File


/**
 * A flag to enter debug mode in GradleRunner.
 * Also applies some hacks, see [bypassTestkitDebugEnvRestriction.kt]
 * and [setUpGradleProjectDir].
 */
const val withDebug = false

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
            // Apply also plugin to print kotlin psecific info.
            plugins.apply(PrintKotlinSpecificInfo::class.java)
        """
    settingsFile.writeText(settingsFileContent.trimIndent())
}