package androidUtils

import TestBase
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.SimplePrintOutputListener
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.checkExitCodeIsZero
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Manages project preparation tasks such as copying and assembling the project.
 */
object ProjectPreparer  {

    /** Path to the directory containing Gradle E2E test projects. */
    private val gradleE2eTestProjectsPath = TestUtil.amperSourcesRoot / "gradle-e2e-test/testData/projects"

    /**
     * Assembles the APK containing the instrumented tests themselves, optionally using a custom [applicationId].
     */
    suspend fun assembleTestApp(applicationId: String? = null) {
        val testApkAppProjectPath = gradleE2eTestProjectsPath / "test-apk/app"
        val testFilePath = testApkAppProjectPath / "src/androidTest/java/com/jetbrains/sample/app/ExampleInstrumentedTest.kt"
        val buildFilePath = testApkAppProjectPath / "build.gradle.kts"
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

        val gradlewFilename = if (TestBase().isWindows) "gradlew.bat" else "gradlew"
        val gradlewPath = TestUtil.amperCheckoutRoot / gradlewFilename

        runProcessAndCaptureOutput(
            command = listOf(
                gradlewPath.pathString,
                "-p",
                testApkAppProjectPath.pathString,
                "createDebugAndroidTestApk"
            ),
            outputListener = SimplePrintOutputListener,
        ).checkExitCodeIsZero()

        // Restore the original content of the test file and build.gradle.kts
        originalTestFileContent?.let {
            testFilePath.writeText(it)
        }

        originalBuildFileContent?.let {
            buildFilePath.writeText(it)
        }
    }

    /**
     * Prepares the [projectName] Android project for testing by configuring Gradle
     * and assembling the test APK.
     */
    suspend fun prepareProjectsAndroidForGradle(projectName: String) {
        val projectDirectory = TestBase().tempProjectsDir/ projectName
        val runWithPluginClasspath = true
        TestBase().putAmperToGradleFile(projectDirectory, runWithPluginClasspath)
        TestBase().assembleTargetApp(projectDirectory)
    }
}
