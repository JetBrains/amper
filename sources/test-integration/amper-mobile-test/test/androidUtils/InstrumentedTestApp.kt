/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package androidUtils

import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.android.AndroidTools
import org.jetbrains.amper.test.gradle.runGradle
import org.junit.jupiter.api.TestReporter
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Manages project preparation tasks such as copying and assembling the project.
 */
object InstrumentedTestApp  {

    /**
     * Path to the Gradle project containing the instrumented test app.
     * This app is installed next to the app under test on the same device, and is responsible for running the tests.
     */
    private val testAppProject = Dirs.amperSourcesRoot / "test-integration/amper-mobile-test/testData/instrumented-tests-app/app"

    /**
     * Assembles the APK containing the instrumented tests themselves, optionally using a custom [applicationId].
     */
    suspend fun assemble(applicationId: String? = null, testReporter: TestReporter): Path {
        val testFilePath = testAppProject / "src/androidTest/java/com/jetbrains/sample/app/ExampleInstrumentedTest.kt"
        val buildFilePath = testAppProject / "build.gradle.kts"
        var originalTestFileContent: String? = null
        var originalBuildFileContent: String? = null

        if (applicationId != null) {
            // Modify the test file to use custom application ID
            originalTestFileContent = testFilePath.readText()
            val updatedTestFileContent = originalTestFileContent.replace("com.jetbrains.sample.app", applicationId)
            testFilePath.writeText(updatedTestFileContent)

            // Modify the build.gradle.kts to use custom application ID
            originalBuildFileContent = buildFilePath.readText()
            val updatedBuildFileContent = originalBuildFileContent.replace(
                "applicationId = \"com.jetbrains.sample.app\"",
                "applicationId = \"$applicationId\""
            ).replace(
                "testApplicationId = \"com.jetbrains.sample.app.test\"",
                "testApplicationId = \"$applicationId.test\""
            )
            buildFilePath.writeText(updatedBuildFileContent)
        }

        try {
            runGradle(
                projectDir = testAppProject,
                args = listOf("createDebugAndroidTestApk"),
                cmdName = "gradle (test-apk)",
                testReporter = testReporter,
                additionalEnv = AndroidTools.getOrInstallForTests().environment(),
            )
        } finally {
            // Restore the original content of the test file and build.gradle.kts
            originalTestFileContent?.let {
                testFilePath.writeText(it)
            }

            originalBuildFileContent?.let {
                buildFilePath.writeText(it)
            }
        }

        return testAppProject / "build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    }
}
