/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalPathApi::class)

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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.core.extract.extractZip
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.util.headlessEmulatorModePropertyName
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

class AmperAndroidExampleProjectsTest : IntegrationTestBase() {

    private val androidTestDataRoot: Path = TestUtil
        .amperSourcesRoot
        .resolve("amper-backend-test/testData/projects/android")

    private fun setupAndroidTestProject(testProjectName: String): ProjectContext =
        setupTestProject(androidTestDataRoot.resolve(testProjectName), copyToTemp = false)

    /**
     * Not running this test in CI for a while, because there is no nested hardware virtualization on the agents.
     */
    @Test
    @OnNonCI
    @Ignore
    fun simple() = runTest(timeout = 15.minutes) {
        val projectContext = setupAndroidTestProject("simple")
        System.setProperty(headlessEmulatorModePropertyName, "true")
        AmperBackend(projectContext, backgroundScope).runTask(TaskName.fromHierarchy(listOf("simple", "runAndroidDebug")))
        val device = AndroidDebugBridge.getBridge().devices.first()
        assertStringInLogcat(device, "My Application")
    }

    @Test
    fun `simple tests debug`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("simple")
        AmperBackend(projectContext, backgroundScope).runTask(TaskName.fromHierarchy(listOf("simple", "testAndroidTestDebug")))
        assertStdoutContains("1 tests successful")
    }

    @Test
    fun `simple tests release`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("simple")
        AmperBackend(projectContext, backgroundScope).runTask(TaskName.fromHierarchy(listOf("simple", "testAndroidTestRelease")))
        assertStdoutContains("1 tests successful")
    }

    @Test
    fun `apk contains dependencies`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("simple")
        val taskName = TaskName.fromHierarchy(listOf("simple", "buildAndroidDebug"))
        AmperBackend(projectContext, backgroundScope).runTask(taskName)
        val apkPath = projectContext.getApkPath(taskName)
        assertClassContainsInApk("Lcom/google/common/collect/Synchronized\$SynchronizedBiMap;", apkPath)
    }

    @Test
    fun `appcompat compiles successfully and contains dependencies`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("appcompat")
        val taskName = TaskName.fromHierarchy(listOf("appcompat", "buildAndroidDebug"))
        AmperBackend(projectContext, backgroundScope).runTask(taskName)
        val apkPath = projectContext.getApkPath(taskName)
        assertClassContainsInApk("Landroidx/appcompat/app/AppCompatActivity;", apkPath)
    }

    @Test
    fun `lib contains lib code and resources`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("lib")
        val taskName = TaskName.fromHierarchy(listOf("lib", "buildAndroidDebug"))
        AmperBackend(projectContext, backgroundScope).runTask(taskName)
        val aarPath = projectContext.getAarPath(taskName)
        assertClassContainsInAar("org.example.namespace.Lib", aarPath)
        assertStringContainsInResources("My Library", aarPath)
    }

    @Test
    fun `it's possible to use AppCompat theme from appcompat library in AndroidManifest`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("appcompat")
        val taskName = TaskName.fromHierarchy(listOf("appcompat", "buildAndroidDebug"))
        AmperBackend(projectContext, backgroundScope).runTask(taskName)
        val apkPath = projectContext.getApkPath(taskName)
        val extractedApkPath = apkPath.parent.resolve("extractedApk")
        extractZip(apkPath, extractedApkPath, false)
        val themeReference = getThemeReferenceFromAndroidManifest(extractedApkPath)
        assertThemeContainsInResources(extractedApkPath / "resources.arsc", themeReference)
    }

    @Test
    fun `task graph is correct for downloading and installing android sdk components`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("simple")
        AmperBackend(projectContext, backgroundScope).showTasks()
        // debug
        assertStdoutContains("task :simple:buildAndroidDebug -> :simple:resolveDependenciesAndroid, :simple:compileAndroidDebug")
        assertStdoutContains("task :simple:compileAndroidDebug -> :simple:transformDependenciesAndroid, :simple:installPlatformAndroid, :simple:prepareAndroidDebug, :simple:resolveDependenciesAndroid")
        assertStdoutContains("task :simple:prepareAndroidDebug -> :simple:installBuildToolsAndroid, :simple:installPlatformToolsAndroid, :simple:installPlatformAndroid, :simple:acceptAndroidLicensesAndroid, :simple:resolveDependenciesAndroid")
        assertStdoutContains("task :simple:runAndroidDebug -> :simple:installSystemImageAndroid, :simple:installEmulatorAndroid, :simple:buildAndroidDebug")
        // release
        assertStdoutContains("task :simple:buildAndroidRelease -> :simple:resolveDependenciesAndroid, :simple:compileAndroidRelease")
        assertStdoutContains("task :simple:compileAndroidRelease -> :simple:transformDependenciesAndroid, :simple:installPlatformAndroid, :simple:prepareAndroidRelease, :simple:resolveDependenciesAndroid")
        assertStdoutContains("task :simple:prepareAndroidRelease -> :simple:installBuildToolsAndroid, :simple:installPlatformToolsAndroid, :simple:installPlatformAndroid, :simple:acceptAndroidLicensesAndroid, :simple:resolveDependenciesAndroid")
        assertStdoutContains("task :simple:runAndroidRelease -> :simple:installSystemImageAndroid, :simple:installEmulatorAndroid, :simple:buildAndroidRelease")

        // transform dependencies
        // main
        assertStdoutContains("task :simple:transformDependenciesAndroid -> :simple:resolveDependenciesAndroid")
        // test
        assertStdoutContains("task :simple:transformDependenciesAndroidTest -> :simple:resolveDependenciesAndroidTest")
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
        val a: BinaryResourceIdentifier = BinaryResourceIdentifier.create(themeReference)
        assertTrue(blamer.resourceEntries.entries().any { it.value.parent().containsResource(a) })
    }

    private fun getThemeReferenceFromAndroidManifest(extractedApkPath: Path): Int {
        val decodedXml = BinaryXmlParser.decodeXml(
            "AndroidManifest.xml",
            Files.readAllBytes(extractedApkPath / "AndroidManifest.xml")
        )
        val decodedXmlString = decodedXml.decodeToString()
        val groups = "android:theme=\"@ref/(.*)\"".toRegex().find(decodedXmlString)
        val hex = groups?.groupValues?.get(1) ?: fail("There is no android theme reference in AndroidManifest.xml")
        val themeReference = hex.removePrefix("0x").toInt(16)
        return themeReference
    }

    private fun assertStringContainsInResources(value: String, aarPath: Path) {
        val extractedAarPath = aarPath.parent.resolve("extractedAar")
        val valuesXml = extractedAarPath
            .walk()
            .filter { it.extension == "xml" }
            .filter { "values" in it.name }
            .firstOrNull() ?: fail("There is no values.xml in AAR")
        assertContains(valuesXml.readText(), value)
    }

    private fun ProjectContext.getAarPath(taskName: TaskName): Path = getTaskOutputPath(taskName)
        .walk()
        .filter { it.extension == "aar" }
        .firstOrNull() ?: fail("AAR not found")

    private fun ProjectContext.getApkPath(taskName: TaskName): Path = getTaskOutputPath(taskName)
        .walk()
        .filter { it.extension == "apk" }
        .firstOrNull() ?: fail("Apk not found")


    private suspend fun waitAndAssertSubstringInOutput(substring: String) {
        stdoutCollector.lines.takeWhile { substring !in it }.collect()
    }

    private fun ProjectContext.getTaskOutputPath(taskName: TaskName): Path =
        buildOutputRoot.path / "tasks" / taskName.name.replace(':', '_')

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

    private fun assertClassContainsInAar(fqn: String, aarPath: Path): Path {
        val extractedAarPath = aarPath.parent.resolve("extractedAar")
        extractZip(aarPath, extractedAarPath, false)

        val classesInJars = extractedAarPath
            .walk()
            .filter { it.extension == "jar" }
            .flatMap { jar ->
                val extractedJarPath = extractedAarPath.resolve(jar.fileName.toString().substringBeforeLast("."))
                extractZip(jar, extractedJarPath, false)
                extractedJarPath.walk()
            }
            .map { extractedAarPath.relativize(it) }
            .map { it.toString() }
            .map { it.replace("\\", "/") }

        assertContains(classesInJars.toList(), "classes/${fqn.replace(".", "/")}.class")
        return extractedAarPath
    }

    private suspend fun TestScope.assertStringInLogcat(device: IDevice, value: String) {
        val deferred = CompletableDeferred(false)
        device.executeShellCommand("logcat -d -v long", object : MultiLineReceiver() {
            override fun isCancelled(): Boolean = coroutineContext.job.isCancelled

            override fun processNewLines(lines: Array<out String>) {
                if (lines.any { it.contains(value) }) {
                    deferred.complete(true)
                }
            }
        })
        deferred.await()
    }
}
