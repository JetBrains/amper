/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnspecifiedDependencyVersion
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import kotlin.io.path.div

class BomTest: BaseModuleDrTest() {

    override val testGoldenFilesRoot: Path = super.testGoldenFilesRoot / "bomSupport"

    /**
     * Version of a direct dependency is resolved from BOM if it was left unspecified.
     */
    @Test
    fun `resolving version of a direct dependency from BOM`() = runTest {
        val aom = getTestProjectModel("jvm-bom-support", testDataRoot)

        val jvmAppDeps = doTest(
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
            verifyMessages = false,
            module = "app",
            expected = """
                Module app
                │ - main
                │ - scope = COMPILE
                │ - platforms = [jvm]
                ├─── app:common:com.fasterxml.jackson.core:jackson-annotations:unspecified
                │    ╰─── com.fasterxml.jackson.core:jackson-annotations:unspecified -> 2.18.3
                │         ╰─── com.fasterxml.jackson:jackson-bom:2.18.3
                │              ╰─── com.fasterxml.jackson.core:jackson-annotations:2.18.3 (c)
                ├─── app:common:com.fasterxml.jackson:jackson-bom:2.18.3
                │    ╰─── com.fasterxml.jackson:jackson-bom:2.18.3 (*)
                ├─── app:common:org.jetbrains.kotlin:kotlin-stdlib:2.1.20, implicit
                │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:2.1.20
                │         ╰─── org.jetbrains:annotations:13.0
                ├─── app:jvm:com.fasterxml.jackson.core:jackson-annotations:unspecified
                │    ╰─── com.fasterxml.jackson.core:jackson-annotations:unspecified -> 2.18.3 (*)
                ├─── app:jvm:com.fasterxml.jackson:jackson-bom:2.18.3
                │    ╰─── com.fasterxml.jackson:jackson-bom:2.18.3 (*)
                ╰─── app:jvm:org.jetbrains.kotlin:kotlin-stdlib:2.1.20, implicit
                     ╰─── org.jetbrains.kotlin:kotlin-stdlib:2.1.20 (*)
            """.trimIndent(),
        )

        assertFiles(
            listOf(
                "annotations-13.0.jar",
                "jackson-annotations-2.18.3.jar",
                "kotlin-stdlib-2.1.20.jar",
            ),
            jvmAppDeps
        )
    }

    /**
     * Version of an exported direct dependency is resolved from BOM if it was left unspecified.
     */
    @Test
    fun `resolving version of an exported direct dependency from BOM`(testInfo: TestInfo) = runTest {
        val aom = getTestProjectModel("jvm-bom-support-exported", testDataRoot)

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
            verifyMessages = false,
            module = "app"
        )

        assertFiles(testInfo,jvmAppDeps)
    }

    /**
     * Version of a transitive dependency is resolved from BOM if it was left unspecified.
     *
     * Module 'app' of the project 'jvm-bom-support-transitive' declares a direct dependency on 'io.github.dokar3:sonner:0.3.8'
     * that in turn depends on 'io.github.dokar3:sonner-android:0.3.8'
     * that in turn depends on 'androidx.compose.ui:ui-tooling-preview'.
     *
     * Published metadata of the library 'io.github.dokar3:sonner-android:0.3.8' doesn't specify
     * a version of 'androidx.compose.ui:ui-tooling-preview'.
     * Consumer of the library have no way to resolve the version without specifying it either explicitly or by using BOM.
     *
     * This tests checks that a version of the dependency 'androidx.compose.ui:ui-tooling-preview'
     * is successfully resolved from the BOM 'androidx.compose:compose-bom:2025.05.00'
     * directly declared along with the dependency on 'io.github.dokar3:sonner:0.3.8' itself.
     */
    @Test
    fun `resolving version of a transitive dependency from BOM`(testInfo: TestInfo) = runTest {
        val aom = getTestProjectModel("jvm-bom-support-unspecified-transitive", testDataRoot)

        val androidAppDeps = doTestByFile(
            testInfo = testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.ClassPathType(
                    ResolutionScope.RUNTIME,
                    setOf(ResolutionPlatform.ANDROID),
                    false,
                    false)
                ,
                ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
            ),
            module = "app",
        )

        assertFiles(testInfo,androidAppDeps)
    }

    /**
     * This tests checks that an unspecified version of a transitive dependency is reported as an error.
     *
     * Module 'app-no-bom' of the project 'jvm-bom-support-transitive' declares a direct dependency on
     * 'io.github.dokar3:sonner:0.3.8'
     * that in turn depends on 'io.github.dokar3:sonner-android:0.3.8'
     * that in turn depends on 'androidx.compose.ui:ui-tooling-preview'.
     *
     * Published metadata of the library 'io.github.dokar3:sonner-android:0.3.8' doesn't specify
     * a version of 'androidx.compose.ui:ui-tooling-preview'.
     * Consumer of the library have no way to resolve the version without specifying it either explicitly or by using BOM.
     *
     * This tests checks that a version of the dependency 'androidx.compose.ui:ui-tooling-preview' is left unspecified
     * and DR reports a corresponding error.
     */
    @Test
    fun `reporting unspecified version of a transitive dependency`(testInfo: TestInfo) = runTest {
        val aom = getTestProjectModel("jvm-unspecified-transitive", testDataRoot)

        val androidAppDeps = doTestByFile(
            testInfo = testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.ClassPathType(
                    ResolutionScope.RUNTIME,
                    setOf(ResolutionPlatform.ANDROID),
                    false,
                    false)
                ,
                ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
            ),
            verifyMessages = false,
            module = "app",
        )

        assertTheOnlyNonInfoMessage(
            root = androidAppDeps,
            transitively = true,
            severity = Severity.ERROR,
            diagnostic = UnspecifiedDependencyVersion
        )
    }
}