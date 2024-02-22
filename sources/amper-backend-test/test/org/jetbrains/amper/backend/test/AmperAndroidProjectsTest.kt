/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalPathApi::class)

package org.jetbrains.amper.backend.test

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.downloader.extractZip
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.util.headlessEmulatorModePropertyName
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

class AmperAndroidProjectsTest : IntegrationTestBase() {
    private val androidProjectsPath: Path = TestUtil.amperCheckoutRoot.resolve("android-projects")
    private val userCacheRoot: AmperUserCacheRoot = AmperUserCacheRoot(TestUtil.userCacheRoot)

    private fun setupAndroidTestProject(testProjectName: String): ProjectContext {
        val projectContext = setupTestProject(androidProjectsPath.resolve(testProjectName), copyToTemp = true)
        projectContext.projectRoot.path.deleteGradleFiles()
        return projectContext
    }

    @Test
    @OnNonCI
    fun `simple-app`() = runTest(timeout = 15.minutes) {
        val projectContext = setupAndroidTestProject("simple-app")
        System.setProperty(headlessEmulatorModePropertyName, "true")
        val job = async { AmperBackend(projectContext).runTask(TaskName.fromHierarchy(listOf("simple-app", "logcat"))) }
        waitAndAssertSubstringInOutput("My Application")
        job.cancelAndJoin()
    }

    @Test
    fun `apk contains dependencies`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("simple-app")
        val taskName = TaskName.fromHierarchy(listOf("simple-app", "buildAndroidDebug"))
        AmperBackend(projectContext).runTask(taskName)
        val apkPath = getTaskOutputPath(projectContext, taskName)
            .walk()
            .filter { it.extension == "apk" }
            .firstOrNull() ?: fail("Apk not found")
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
        assertContains(typesInDexes.toList(), "Lcom/google/common/collect/Synchronized\$SynchronizedBiMap;")
    }

    @Test
    fun `lib contains lib code and resources`() = runTestInfinitely {
        val projectContext = setupAndroidTestProject("simple-lib")
        val taskName = TaskName.fromHierarchy(listOf("simple-lib", "buildAndroidDebug"))
        AmperBackend(projectContext).runTask(taskName)

        val aarPath = getTaskOutputPath(projectContext, taskName)
            .walk()
            .filter { it.extension == "aar" }
            .firstOrNull() ?: fail("AAR not found")

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

        assertContains(classesInJars.toList(), "classes/org/example/namespace/Lib.class")

        val valuesXml = extractedAarPath
            .walk()
            .filter { it.extension == "xml" }
            .filter { "values" in it.name }
            .firstOrNull() ?: fail("There is no values.xml in AAR")

        assertContains(valuesXml.readText(), "My Library")
    }

    @Test
    fun `task graph is correct for downloading and installing android sdk components`() {
        val projectContext = setupAndroidTestProject("simple-app")
        AmperBackend(projectContext).showTasks()
        // debug
        assertStdoutContains("task :simple-app:buildAndroidDebug -> :simple-app:resolveDependenciesAndroid, :simple-app:compileAndroidDebug")
        assertStdoutContains("task :simple-app:compileAndroidDebug -> :simple-app:resolveDependenciesAndroid, :simple-app:prepareBuildAndroidDebug, :simple-app:downloadSdkAndroid")
        assertStdoutContains("task :simple-app:prepareBuildAndroidDebug -> :simple-app:downloadBuildTools, :simple-app:downloadPlatformTools, :simple-app:downloadSdkAndroid")
        assertStdoutContains("task :simple-app:runAndroidDebug -> :simple-app:buildAndroidDebug, :simple-app:downloadSystemImage, :simple-app:downloadAndroidEmulator")
        // release
        assertStdoutContains("task :simple-app:buildAndroidRelease -> :simple-app:resolveDependenciesAndroid, :simple-app:compileAndroidRelease")
        assertStdoutContains("task :simple-app:compileAndroidRelease -> :simple-app:resolveDependenciesAndroid, :simple-app:prepareBuildAndroidRelease, :simple-app:downloadSdkAndroid")
        assertStdoutContains("task :simple-app:prepareBuildAndroidRelease -> :simple-app:downloadBuildTools, :simple-app:downloadPlatformTools, :simple-app:downloadSdkAndroid")
        assertStdoutContains("task :simple-app:runAndroidRelease -> :simple-app:buildAndroidRelease, :simple-app:downloadSystemImage, :simple-app:downloadAndroidEmulator")
    }

    private suspend fun waitAndAssertSubstringInOutput(substring: String) {
        stdoutCollector.lines.takeWhile { !it.contains(substring) }.count()
    }

    private fun getTaskOutputPath(projectContext: ProjectContext, taskName: TaskName): Path =
        projectContext.buildOutputRoot.path.resolve("tasks").resolve(taskName.name.replace(':', '_'))
}
