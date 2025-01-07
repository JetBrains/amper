/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.android.tools.apk.analyzer.BinaryXmlParser
import com.google.devrel.gmscore.tools.apk.arsc.ArscBlamer
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceIdentifier
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.job
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.core.extract.extractZip
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.test.OnNonCI
import org.jetbrains.amper.test.TestCollector
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.util.headlessEmulatorModePropertyName
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

class AmperAndroidExampleProjectsTest : AmperIntegrationTestBase() {

    private val androidTestDataRoot: Path = TestUtil
        .amperSourcesRoot
        .resolve("amper-backend-test/testData/projects/android")

    private suspend fun TestCollector.setupAndroidTestProject(
        testProjectName: String,
        useEmptyAndroidHome: Boolean = false,
    ): CliContext = setupTestProject(
        androidTestDataRoot.resolve(testProjectName),
        copyToTemp = false,
        useEmptyAndroidHome = useEmptyAndroidHome,
    )

    /**
     * Not running this test in CI for a while, because there is no nested hardware virtualization on the agents.
     */
    @Test
    @OnNonCI
    @Ignore
    fun simple() = runTestWithCollector(timeout = 15.minutes) {
        val projectContext = setupAndroidTestProject("simple")
        System.setProperty(headlessEmulatorModePropertyName, "true")
        AmperBackend(projectContext).runTask("simple", "runAndroidDebug")
        val device = AndroidDebugBridge.getBridge().devices.first()
        assertStringInLogcat(device, "My Application")
    }

    @Test
    fun `simple tests debug`() = runTestWithCollector {
        val projectContext = setupAndroidTestProject("simple")
        AmperBackend(projectContext).runTask("simple", "testAndroidTestDebug")
        assertStdoutContains("1 tests successful")
    }

    @Test
    fun `simple tests release`() = runTestWithCollector {
        val projectContext = setupAndroidTestProject("simple")
        AmperBackend(projectContext).runTask("simple", "testAndroidTestRelease")
        assertStdoutContains("1 tests successful")
    }

    @Test
    fun `apk contains dependencies`() = runTestWithCollector {
        val projectContext = setupAndroidTestProject("simple")
        val taskName = TaskName.fromHierarchy(listOf("simple", "buildAndroidDebug"))
        AmperBackend(projectContext).runTask(taskName)
        val apkPath = projectContext.getArtifactPath(taskName)
        assertClassContainsInApk("Lcom/google/common/collect/Synchronized\$SynchronizedBiMap;", apkPath)
    }

    @Test
    fun `appcompat compiles successfully and contains dependencies`() = runTestWithCollector {
        val projectContext = setupAndroidTestProject("appcompat")
        val taskName = TaskName.fromHierarchy(listOf("appcompat", "buildAndroidDebug"))
        AmperBackend(projectContext).runTask(taskName)
        val apkPath = projectContext.getArtifactPath(taskName)
        assertClassContainsInApk("Landroidx/appcompat/app/AppCompatActivity;", apkPath)
    }

    @Test
    fun `it's possible to use AppCompat theme from appcompat library in AndroidManifest`() = runTestWithCollector {
        val projectContext = setupAndroidTestProject("appcompat")
        val taskName = TaskName.fromHierarchy(listOf("appcompat", "buildAndroidDebug"))
        AmperBackend(projectContext).runTask(taskName)
        val apkPath = projectContext.getArtifactPath(taskName)
        val extractedApkPath = apkPath.parent.resolve("extractedApk")
        extractZip(apkPath, extractedApkPath, false)
        val themeReference = getThemeReferenceFromAndroidManifest(extractedApkPath)
        assertThemeContainsInResources(extractedApkPath / "resources.arsc", themeReference)
    }

    @Test
    fun `should fail when license is not accepted`() = runTestWithCollector {
        val projectContext = setupAndroidTestProject("simple", useEmptyAndroidHome = true)
        val sdkManagerPath = projectContext.androidHomeRoot.path / "cmdline-tools/latest/bin/sdkmanager"

        val throwable = assertFails {
            AmperBackend(projectContext).build()
        }

        // The missing license should be the one from the cmdline-tools, which is the only thing that was installed in
        // this empty SDK home. Since we install the latest version, we sometimes get the regular license and sometimes
        // the preview. That's why we need this 2-choice assertion.
        val expectedError1 = unacceptedLicenseMessage(sdkManagerPath, "android-sdk-license")
        val expectedError2 = unacceptedLicenseMessage(sdkManagerPath, "android-sdk-preview-license")
        assertTrue(
            throwable.message in setOf(expectedError1, expectedError2),
            "Unexpected error message:\n\n${throwable.message}\n\nExpected either:\n\n$expectedError1\n\nor:\n\n$expectedError2",
        )
    }

    private fun unacceptedLicenseMessage(sdkManagerPath: Path, licenseName: String) = """
        Task ':simple:checkAndroidSdkLicenseAndroid' failed: Some licenses have not been accepted in the Android SDK:
         - $licenseName
        Run "$sdkManagerPath --licenses" to review and accept them
    """.trimIndent()

    @Test
    fun `bundle without signing enabled has no signature`() = runTestWithCollector {
        val projectContext = setupAndroidTestProject("simple")
        val taskName = TaskName.fromHierarchy(listOf("simple", "bundleAndroid"))
        AmperBackend(projectContext).runTask(taskName)
        val bundlePath = projectContext.getArtifactPath(taskName, "aab")
        assertFileWithExtensionDoesNotContainInBundle("RSA", bundlePath)
    }

    @Test
    fun `bundle with signing enabled and properties file has signature`() = runTestWithCollector {
        val projectContext = setupAndroidTestProject("signed")
        val taskName = TaskName.fromHierarchy(listOf("signed", "bundleAndroid"))
        AmperBackend(projectContext).runTask(taskName)
        val bundlePath = projectContext.getArtifactPath(taskName, "aab")
        assertFileContainsInBundle("ALIAS.RSA", bundlePath)
    }

    @Test
    fun `task graph is correct for downloading and installing android sdk components`() = runTestWithCollector {
        val projectContext = setupAndroidTestProject("simple")
        AmperBackend(projectContext).showTasks()
        // debug
        assertStdoutContains("task :simple:buildAndroidDebug -> :simple:runtimeClasspathAndroid")
        assertStdoutContains("task :simple:compileAndroidDebug -> :simple:installPlatformAndroid, :simple:transformDependenciesAndroid, :simple:resolveDependenciesAndroid, :simple:prepareAndroidDebug")
        assertStdoutContains("task :simple:prepareAndroidDebug -> :simple:installBuildToolsAndroid, :simple:installPlatformToolsAndroid, :simple:installPlatformAndroid, :simple:resolveDependenciesAndroid")
        assertStdoutContains("task :simple:runAndroidDebug -> :simple:installSystemImageAndroid, :simple:installEmulatorAndroid, :simple:buildAndroidDebug")
        // release
        assertStdoutContains("task :simple:buildAndroidRelease -> :simple:runtimeClasspathAndroid")
        assertStdoutContains("task :simple:compileAndroidRelease -> :simple:installPlatformAndroid, :simple:transformDependenciesAndroid, :simple:resolveDependenciesAndroid, :simple:prepareAndroidRelease")
        assertStdoutContains("task :simple:prepareAndroidRelease -> :simple:installBuildToolsAndroid, :simple:installPlatformToolsAndroid, :simple:installPlatformAndroid, :simple:resolveDependenciesAndroid")
        assertStdoutContains("task :simple:runAndroidRelease -> :simple:installSystemImageAndroid, :simple:installEmulatorAndroid, :simple:buildAndroidRelease")

        // transform dependencies
        // main
        assertStdoutContains("task :simple:transformDependenciesAndroid -> :simple:resolveDependenciesAndroid")
        // test
        assertStdoutContains("task :simple:transformDependenciesAndroidTest -> :simple:resolveDependenciesAndroidTest")

        // to accept android sdk license, we need cmdline tools
        assertStdoutContains("task :simple:checkAndroidSdkLicenseAndroid -> :simple:installCmdlineToolsAndroid")

        // Android sdk components can be installed separately, we need to check android sdk licenses every time.
        // Since scheduling ensures that only one instance of a task executed during the build, checking android sdk
        // license will be performed only once
        assertStdoutContains("task :simple:installBuildToolsAndroid -> :simple:checkAndroidSdkLicenseAndroid")
        assertStdoutContains("task :simple:installBuildToolsAndroidTest -> :simple:checkAndroidSdkLicenseAndroid")
        assertStdoutContains("task :simple:installEmulatorAndroid -> :simple:checkAndroidSdkLicenseAndroid")
        assertStdoutContains("task :simple:installEmulatorAndroidTest -> :simple:checkAndroidSdkLicenseAndroid")
        assertStdoutContains("task :simple:installPlatformAndroid -> :simple:checkAndroidSdkLicenseAndroid")
        assertStdoutContains("task :simple:installPlatformAndroidTest -> :simple:checkAndroidSdkLicenseAndroid")
        assertStdoutContains("task :simple:installPlatformToolsAndroid -> :simple:checkAndroidSdkLicenseAndroid")
        assertStdoutContains("task :simple:installPlatformToolsAndroidTest -> :simple:checkAndroidSdkLicenseAndroid")
        assertStdoutContains("task :simple:installSystemImageAndroid -> :simple:checkAndroidSdkLicenseAndroid")
        assertStdoutContains("task :simple:installSystemImageAndroidTest -> :simple:checkAndroidSdkLicenseAndroid")
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

    private fun CliContext.getArtifactPath(taskName: TaskName, extension: String = "apk"): Path = getTaskOutputPath(taskName)
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

    private suspend fun TestCollector.assertStringInLogcat(device: IDevice, value: String) {
        val deferred = CompletableDeferred(false)
        device.executeShellCommand("logcat -d -v long", object : MultiLineReceiver() {
            override fun isCancelled(): Boolean = backgroundScope.coroutineContext.job.isCancelled

            override fun processNewLines(lines: Array<out String>) {
                if (lines.any { it.contains(value) }) {
                    deferred.complete(true)
                }
            }
        })
        deferred.await()
    }
}
