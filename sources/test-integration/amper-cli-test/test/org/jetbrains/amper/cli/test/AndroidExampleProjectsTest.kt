/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import com.android.tools.apk.analyzer.BinaryXmlParser
import com.google.devrel.gmscore.tools.apk.arsc.ArscBlamer
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceIdentifier
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.getTaskOutputPath
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.core.extract.extractZip
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.Dirs
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AndroidExampleProjectsTest : AmperCliTestBase() {

    @Test
    fun `simple tests debug`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("android/simple"),
            "task", ":simple:testAndroidDebug",
        )
        result.assertStdoutContains("1 tests successful")
    }

    @Test
    fun `simple tests release`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("android/simple"),
            "task", ":simple:testAndroidRelease",
        )
        result.assertStdoutContains("1 tests successful")
    }

    @Test
    fun `apk contains dependencies`() = runSlowTest {
        val taskName = ":simple:buildAndroidDebug"
        val result = runCli(
            projectRoot = testProject("android/simple"),
            "task", taskName,
        )
        val apkPath = result.getArtifactPath(taskName)
        assertClassContainsInApk("Lcom/google/common/collect/Synchronized\$SynchronizedBiMap;", apkPath)
    }

    @Test
    fun `appcompat compiles successfully and contains dependencies`() = runSlowTest {
        val taskName = ":appcompat:buildAndroidDebug"
        val result = runCli(projectRoot = testProject("android/appcompat"), "task", taskName)
        val apkPath = result.getArtifactPath(taskName)
        assertClassContainsInApk("Landroidx/appcompat/app/AppCompatActivity;", apkPath)
    }

    @Test
    fun `it's possible to use AppCompat theme from appcompat library in AndroidManifest`() = runSlowTest {
        val taskName = ":appcompat:buildAndroidDebug"
        val result = runCli(projectRoot = testProject("android/appcompat"), "task", taskName)
        val apkPath = result.getArtifactPath(taskName)
        val extractedApkPath = apkPath.parent.resolve("extractedApk")
        extractZip(apkPath, extractedApkPath, false)
        val themeReference = getThemeReferenceFromAndroidManifest(extractedApkPath)
        assertThemeContainsInResources(extractedApkPath / "resources.arsc", themeReference)
    }

    @Test
    fun `should fail when license is not accepted`() = runSlowTest {
        val androidSdkHome = (Dirs.tempDir / "empty-android-sdk").also { it.createDirectories() }
        val result = runCli(
            projectRoot = testProject("android/simple"),
            "build",
            configureAndroidHome = false,
            environment = mapOf("ANDROID_HOME" to androidSdkHome.absolutePathString()),
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        val sdkManagerPath = androidSdkHome / "cmdline-tools/latest/bin/sdkmanager"

        // The missing license should be the one from the cmdline-tools, which is the only thing that was installed in
        // this empty SDK home. Since we install the latest version, we sometimes get the regular license and sometimes
        // the preview. That's why we need this 2-choice assertion.
        val expectedError1 = unacceptedLicenseMessage(sdkManagerPath, "android-sdk-license")
        val expectedError2 = unacceptedLicenseMessage(sdkManagerPath, "android-sdk-preview-license")
        if ("preview" in result.stderr) {
            assertContains(result.stderr, expectedError2)
        } else {
            assertContains(result.stderr, expectedError1)
        }
    }

    private fun unacceptedLicenseMessage(sdkManagerPath: Path, licenseName: String) = """
        Task ':simple:checkAndroidSdkLicenseAndroid' failed: Some licenses have not been accepted in the Android SDK:
         - $licenseName
        Run "$sdkManagerPath --licenses" to review and accept them
    """.trimIndent()

    @Test
    fun `bundle without signing enabled has no signature`() = runSlowTest {
        val taskName = ":simple:bundleAndroid"
        val result = runCli(projectRoot = testProject("android/simple"), "task", taskName)
        val bundlePath = result.getArtifactPath(taskName, "aab")
        assertFileWithExtensionDoesNotContainInBundle("RSA", bundlePath)
    }

    @Test
    fun `bundle with signing enabled and properties file has signature`() = runSlowTest {
        val taskName = ":signed:bundleAndroid"
        val result = runCli(projectRoot = testProject("android/signed"), "task", taskName)
        val bundlePath = result.getArtifactPath(taskName, "aab")
        assertFileContainsInBundle("ALIAS.RSA", bundlePath)
    }

    @Test
    fun `task graph is correct for downloading and installing android sdk components`() = runSlowTest {
        val result = runCli(projectRoot = testProject("android/simple"), "show", "tasks")
        // debug
        result.assertStdoutContains("task :simple:buildAndroidDebug -> :simple:runtimeClasspathAndroid")
        result.assertStdoutContains("task :simple:compileAndroidDebug -> :simple:installPlatformAndroid, :simple:transformDependenciesAndroid, :simple:resolveDependenciesAndroid, :simple:prepareAndroidDebug")
        result.assertStdoutContains("task :simple:prepareAndroidDebug -> :simple:installBuildToolsAndroid, :simple:installPlatformToolsAndroid, :simple:installPlatformAndroid, :simple:resolveDependenciesAndroid")
        result.assertStdoutContains("task :simple:runAndroidDebug -> :simple:installSystemImageAndroid, :simple:installEmulatorAndroid, :simple:buildAndroidDebug")
        // release
        result.assertStdoutContains("task :simple:buildAndroidRelease -> :simple:runtimeClasspathAndroid")
        result.assertStdoutContains("task :simple:compileAndroidRelease -> :simple:installPlatformAndroid, :simple:transformDependenciesAndroid, :simple:resolveDependenciesAndroid, :simple:prepareAndroidRelease")
        result.assertStdoutContains("task :simple:prepareAndroidRelease -> :simple:installBuildToolsAndroid, :simple:installPlatformToolsAndroid, :simple:installPlatformAndroid, :simple:resolveDependenciesAndroid")
        result.assertStdoutContains("task :simple:runAndroidRelease -> :simple:installSystemImageAndroid, :simple:installEmulatorAndroid, :simple:buildAndroidRelease")

        // transform dependencies
        // main
        result.assertStdoutContains("task :simple:transformDependenciesAndroid -> :simple:resolveDependenciesAndroid")
        // test
        result.assertStdoutContains("task :simple:transformDependenciesAndroidTest -> :simple:resolveDependenciesAndroidTest")

        // to accept android sdk license, we need cmdline tools
        result.assertStdoutContains("task :simple:checkAndroidSdkLicenseAndroid -> :simple:installCmdlineToolsAndroid")

        // Android sdk components can be installed separately, we need to check android sdk licenses every time.
        // Since scheduling ensures that only one instance of a task executed during the build, checking android sdk
        // license will be performed only once
        result.assertStdoutContains("task :simple:installBuildToolsAndroid -> :simple:checkAndroidSdkLicenseAndroid")
        result.assertStdoutContains("task :simple:installBuildToolsAndroidTest -> :simple:checkAndroidSdkLicenseAndroid")
        result.assertStdoutContains("task :simple:installEmulatorAndroid -> :simple:checkAndroidSdkLicenseAndroid")
        result.assertStdoutContains("task :simple:installEmulatorAndroidTest -> :simple:checkAndroidSdkLicenseAndroid")
        result.assertStdoutContains("task :simple:installPlatformAndroid -> :simple:checkAndroidSdkLicenseAndroid")
        result.assertStdoutContains("task :simple:installPlatformAndroidTest -> :simple:checkAndroidSdkLicenseAndroid")
        result.assertStdoutContains("task :simple:installPlatformToolsAndroid -> :simple:checkAndroidSdkLicenseAndroid")
        result.assertStdoutContains("task :simple:installPlatformToolsAndroidTest -> :simple:checkAndroidSdkLicenseAndroid")
        result.assertStdoutContains("task :simple:installSystemImageAndroid -> :simple:checkAndroidSdkLicenseAndroid")
        result.assertStdoutContains("task :simple:installSystemImageAndroidTest -> :simple:checkAndroidSdkLicenseAndroid")
    }

    @Test
    fun `package command produce aab bundle`() = runSlowTest {
        val taskName = ":signed:bundleAndroid"
        val result = runCli(projectRoot = testProject("android/signed"), "package")
        val bundlePath = result.getArtifactPath(taskName, "aab")
        assertFileContainsInBundle("ALIAS.RSA", bundlePath)
    }

    @Test
    fun `mockable jar unit tests`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("android/mockable-jar"), 
            "test",
            assertEmptyStdErr = false // Allow stderr output from Mockito warnings
        )
        result.assertStdoutContains("5 tests successful")
    }

    @Test
    fun `robolectric unit tests`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("android/robolectric"),
            "test",
            assertEmptyStdErr = false // Allow stderr output from Robolectric
        )
        result.assertStdoutContains("1 tests successful")
    }


    @AfterTest
    fun tearDown() {
        ConnectorServices.reset()
    }

    private fun assertThemeContainsInResources(resourcesPath: Path, themeReference: Int) {
        val res = BinaryResourceFile((resourcesPath).readBytes())
        val chunk = res.chunks[0] as ResourceTableChunk
        val blamer = ArscBlamer(chunk)
        blamer.blame()
        val a = BinaryResourceIdentifier.create(themeReference)
        assertTrue(blamer.typeChunks.any { it.containsResource(a) })
    }

    private fun getThemeReferenceFromAndroidManifest(extractedApkPath: Path): Int {
        val decodedXml = BinaryXmlParser.decodeXml(
            "AndroidManifest.xml",
            (extractedApkPath / "AndroidManifest.xml").readBytes()
        )
        val decodedXmlString = decodedXml.decodeToString()
        val groups = "android:theme=\"@ref/(.*)\"".toRegex().find(decodedXmlString)
        val hex = groups?.groupValues?.get(1) ?: fail("There is no android theme reference in AndroidManifest.xml")
        val themeReference = hex.removePrefix("0x").toInt(16)
        return themeReference
    }

    private fun AmperCliResult.getArtifactPath(taskName: String, extension: String = "apk"): Path =
        getTaskOutputPath(taskName)
            .walk(PathWalkOption.BREADTH_FIRST)
            .filter { it.extension.lowercase() == extension.lowercase() }
            .firstOrNull() ?: fail("artifact not found")

    private fun assertClassContainsInApk(dalvikFqn: String, apkPath: Path) {
        val extractedApkPath = apkPath.parent.resolve("extractedApk")
        extractZip(apkPath, extractedApkPath, false)
        val typesInDexes = extractedApkPath
            .walk()
            .filter { it.extension == "dex" }
            .flatMap { dex ->
                val dexFile = DexFileFactory.loadDexFile(dex.toFile(), Opcodes.forApi(34))
                dexFile.classes
            }
            .map { it.type }
        assertContains(typesInDexes.toList(), dalvikFqn)
    }

    private fun assertFileContainsInBundle(fileName: String, bundlePath: Path) {
        val extractedAabPath = bundlePath.parent.resolve("extractedBundle")
        extractZip(bundlePath, extractedAabPath, false)
        val files = extractedAabPath
            .walk()
            .map { it.name }
        assertContains(files.toList(), fileName)
    }

    private fun assertFileWithExtensionDoesNotContainInBundle(extension: String, bundlePath: Path) {
        val extractedApkPath = bundlePath.parent.resolve("extractedBundle")
        extractZip(bundlePath, extractedApkPath, false)
        val typesInDexes = extractedApkPath
            .walk()
            .map { it.extension }
            .filter { it.lowercase() == extension.lowercase() }
        assertEquals(typesInDexes.toList().size, 0)
    }
}
