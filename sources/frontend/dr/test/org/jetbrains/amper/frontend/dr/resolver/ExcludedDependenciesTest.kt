/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import org.jetbrains.amper.dependency.resolution.IncrementalCacheUsage
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.test.runTestRespectingDelays
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.div
import kotlin.test.Test

class ExcludedDependenciesTest: BaseModuleDrTest() {

    override val testGoldenFilesRoot: Path = super.testGoldenFilesRoot / "excludedDependencies"

    @TempDir
    lateinit var tmpDir: Path

    fun uniqueNestedTempDir(): Path = tmpDir.resolve(UUID.randomUUID().toString().substring(0, 10))

    /**
     * This test checks that dependency declared in the excludedDependencies list (settings.internal.excludedDependencies)
     * is not added to the dependency graph
     */
    @Test
    fun `check excludedDependencies setting`(testInfo: TestInfo) = runModuleDependenciesTest {
        val aom = getTestProjectModel("jvm-excluded-dependencies-setting", testDataRoot)

        val jvmAppDeps = doTestByFile(
            testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.ClassPathType(
                    ResolutionScope.COMPILE,
                    setOf(ResolutionPlatform.JVM),
                    false,
                    false)
                ,
                ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
            ),
            verifyMessages = true,
            module = "app"
        )

        assertFiles(testInfo,jvmAppDeps)
    }

    /**
     * This test checks that changing the excludedDependencies setting invalidates the incremental cache result even if
     * the input graph has not been changed.
     */
    @Test
    fun `check excludedDependencies affects cache invalidation`(testInfo: TestInfo) = runTestRespectingDelays(
        context = EmptyCoroutineContext + IncrementalCacheUsageContextElement(IncrementalCacheUsage.USE),
    ) {
        // 0. Prepare a temp project location since we plan to modify it.
        val uniqueNestedTempDir = uniqueNestedTempDir()
        val tmpProjectRoot = uniqueNestedTempDir.resolve("project")
        copyTestProjectTo("jvm-excluded-dependencies-setting", testDataRoot, tmpProjectRoot)

        val incrementalCache = IncrementalCache(uniqueNestedTempDir.resolve(".inc"), "1")

        val aom = getTestProjectModel(tmpProjectRoot)

        val jvmAppDeps = doTestByFile(
            testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.ClassPathType(
                    ResolutionScope.COMPILE,
                    setOf(ResolutionPlatform.JVM),
                    false,
                    false)
                ,
                ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                incrementalCache = incrementalCache
            ),
            verifyMessages = true,
            module = "app"
        )

        assertFiles(testInfo,jvmAppDeps)

        // 2. Update settings (removing "com.intellij.platform:kotlinx-coroutines-core-jvm" from excludeDependencies)
        val moduleYaml = tmpProjectRoot.resolve("app").resolve("module.yaml").toFile()
        val moduleYamlText = moduleYaml.readText()
        val patchedModuleYamlText = moduleYamlText.replace("com.intellij.platform:kotlinx-coroutines-core-jvm", "x:y")
        moduleYaml.writeText(patchedModuleYamlText)

        val aomPatched = getTestProjectModel(tmpProjectRoot)
        val jvmAppDepsPatched = doTestByFile(
            testInfo,
            aomPatched,
            ResolutionInput(
                DependenciesFlowType.ClassPathType(
                    ResolutionScope.COMPILE,
                    setOf(ResolutionPlatform.JVM),
                    false,
                    false)
                ,
                ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                incrementalCache = incrementalCache
            ),
            verifyMessages = true,
            module = "app",
            goldenFileName = "${testInfo.testMethod.get().name}_patched"
        )
    }
}