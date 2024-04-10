/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalPathApi::class)

package org.jetbrains.amper.backend.test

import com.android.tools.apk.analyzer.BinaryXmlParser
import com.google.devrel.gmscore.tools.apk.arsc.ArscBlamer
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceIdentifier
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
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
import org.junit.jupiter.api.AfterEach
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
    private val androidProjectsPath: Path = TestUtil.amperCheckoutRoot.resolve("examples.pure")

    private fun setupAndroidTestProject(testProjectName: String): ProjectContext =
        setupTestProject(androidProjectsPath.resolve(testProjectName), copyToTemp = false)

    /**
     * When the emulator already is in working state, app install and launch activity occurring so fast that logcat
     * listener executed after launch couldn't catch the required line. So the only way to catch the line is to start
     * listening to logcat right after emulator launch, but it could violate build system concepts, because now we need
     * to pass a state between tasks
     *
     * So it's better just to ignore this test, at least for now and wait until the proper instrumented tests are ready
     * in pure Amper
     */
    @Test
    @OnNonCI
    @Ignore("Waiting for instrumented test support in pure Amper")
    fun `android-simple`() = runTest(timeout = 15.minutes) {
        val projectContext = setupAndroidTestProject("android-simple")
        System.setProperty(headlessEmulatorModePropertyName, "true")
        val job = async { AmperBackend(projectContext).runTask(TaskName.fromHierarchy(listOf("android-simple", "logcat"))) }
        waitAndAssertSubstringInOutput("My Application")
        job.cancelAndJoin()
    }

    @Test
    fun `android-simple tests debug`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("android-simple")
        AmperBackend(projectContext).runTask(TaskName.fromHierarchy(listOf("android-simple", "testAndroidTestDebug")))
        assertStdoutContains("1 tests successful")
    }

    @Test
    fun `android-simple tests release`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("android-simple")
        AmperBackend(projectContext).runTask(TaskName.fromHierarchy(listOf("android-simple", "testAndroidTestRelease")))
        assertStdoutContains("1 tests successful")
    }

    @Test
    fun `apk contains dependencies`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("android-simple")
        val taskName = TaskName.fromHierarchy(listOf("android-simple", "buildAndroidDebug"))
        AmperBackend(projectContext).runTask(taskName)
        val apkPath = projectContext.getApkPath(taskName)
        assertClassContainsInApk("Lcom/google/common/collect/Synchronized\$SynchronizedBiMap;", apkPath)
    }

    @Test
    fun `android-aar compiles successfully and contains dependencies`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("android-aar")
        val taskName = TaskName.fromHierarchy(listOf("android-aar", "buildAndroidDebug"))
        AmperBackend(projectContext).runTask(taskName)
        val apkPath = projectContext.getApkPath(taskName)
        assertClassContainsInApk("Lcom/github/dkharrat/nexusdialog/FormActivity;", apkPath)
    }

    @Test
    fun `lib contains lib code and resources`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("android-lib")
        val taskName = TaskName.fromHierarchy(listOf("android-lib", "buildAndroidDebug"))
        AmperBackend(projectContext).runTask(taskName)
        val aarPath = projectContext.getAarPath(taskName)
        assertClassContainsInAar("org.example.namespace.Lib", aarPath)
        assertStringContainsInResources("My Library", aarPath)
    }

    @Test
    fun `it's possible to use AppCompat theme from appcompat library in AndroidManifest`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("android-appcompat")
        val taskName = TaskName.fromHierarchy(listOf("android-appcompat", "buildAndroidDebug"))
        AmperBackend(projectContext).runTask(taskName)
        val apkPath = projectContext.getApkPath(taskName)
        val extractedApkPath = apkPath.parent.resolve("extractedApk")
        extractZip(apkPath, extractedApkPath, false)
        val themeReference = getThemeReferenceFromAndroidManifest(extractedApkPath)
        assertThemeContainsInResources(extractedApkPath / "resources.arsc", themeReference)
    }

    @Test
    fun `task graph is correct for downloading and installing android sdk components`() {
        val projectContext = setupAndroidTestProject("android-simple")
        AmperBackend(projectContext).showTasks()
        // debug
        assertStdoutContains("task :android-simple:buildAndroidDebug -> :android-simple:resolveDependenciesAndroid, :android-simple:compileAndroidDebug")
        assertStdoutContains("task :android-simple:compileAndroidDebug -> :android-simple:transformDependenciesAndroid, :android-simple:installPlatformAndroid, :android-simple:prepareAndroidDebug, :android-simple:resolveDependenciesAndroid")
        assertStdoutContains("task :android-simple:prepareAndroidDebug -> :android-simple:installBuildToolsAndroid, :android-simple:installPlatformToolsAndroid, :android-simple:installPlatformAndroid, :android-simple:resolveDependenciesAndroid")
        assertStdoutContains("task :android-simple:runAndroidDebug -> :android-simple:installSystemImageAndroid, :android-simple:installEmulatorAndroid, :android-simple:buildAndroidDebug")
        // release
        assertStdoutContains("task :android-simple:buildAndroidRelease -> :android-simple:resolveDependenciesAndroid, :android-simple:compileAndroidRelease")
        assertStdoutContains("task :android-simple:compileAndroidRelease -> :android-simple:transformDependenciesAndroid, :android-simple:installPlatformAndroid, :android-simple:prepareAndroidRelease, :android-simple:resolveDependenciesAndroid")
        assertStdoutContains("task :android-simple:prepareAndroidRelease -> :android-simple:installBuildToolsAndroid, :android-simple:installPlatformToolsAndroid, :android-simple:installPlatformAndroid, :android-simple:resolveDependenciesAndroid")
        assertStdoutContains("task :android-simple:runAndroidRelease -> :android-simple:installSystemImageAndroid, :android-simple:installEmulatorAndroid, :android-simple:buildAndroidRelease")

        // transform dependencies
        // main
        assertStdoutContains("task :android-simple:transformDependenciesAndroid -> :android-simple:resolveDependenciesAndroid")
        // test
        assertStdoutContains("task :android-simple:transformDependenciesAndroidTest -> :android-simple:resolveDependenciesAndroidTest")
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
}
