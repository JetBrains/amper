/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyConstraintNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.originalVersion
import org.jetbrains.amper.dependency.resolution.resolvedVersion
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DependencyInsightsTest : BaseModuleDrTest() {
    @Test
    fun `test sync empty jvm module`() {
        val aom = getTestProjectModel("jvm-empty", testDataRoot)

        assertEquals(
            setOf("common", "commonTest", "jvm", "jvmTest"),
            aom.modules[0].fragments.map { it.name }.toSet(),
            "",
        )

        val jvmEmptyModuleGraph = runBlocking {
            doTest(
                aom,
                resolutionInput = ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "jvm-empty",
                expected = """
module:jvm-empty
+--- jvm-empty:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|         \--- org.jetbrains:annotations:13.0
+--- jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
+--- jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}
|         +--- org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}
|         |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
|         \--- junit:junit:4.13.2
|              \--- org.hamcrest:hamcrest-core:1.3
+--- jvm-empty:jvm:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
+--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
\--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
     \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion} (*)
""".trimIndent()
            )
        }

        runBlocking {
            assertInsight(
                group = "org.jetbrains.kotlin",
                module = "kotlin-stdlib",
                graph = jvmEmptyModuleGraph,
                expected = """
module:jvm-empty
+--- jvm-empty:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
+--- jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
+--- jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}
|         \--- org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}
|              \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
+--- jvm-empty:jvm:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
+--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
\--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
     \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion} (*)""".trimIndent()
            )
            assertInsight(
                group = "org.hamcrest",
                module = "hamcrest-core",
                graph = jvmEmptyModuleGraph,
                expected = """
module:jvm-empty
+--- jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}
|         \--- junit:junit:4.13.2
|              \--- org.hamcrest:hamcrest-core:1.3
\--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
     \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion} (*)""".trimIndent()
            )
            assertInsight(
                group = "org.jetbrains.kotlin",
                module = "kotlin-test-junit",
                graph = jvmEmptyModuleGraph,
                expected = """
module:jvm-empty
+--- jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}
\--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
     \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}""".trimIndent()
            )
            assertInsight(
                group = "org.jetbrains.kotlin", module = "XXX", graph = jvmEmptyModuleGraph,
                expected = "module:jvm-empty"
            )
            assertInsight(
                group = "XXX", module = "kotlin-test-junit", graph = jvmEmptyModuleGraph,
                expected = "module:jvm-empty"
            )
        }
    }

    @Test
    fun `test compose-multiplatform - shared compile dependencies insights`() {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val sharedModuleIosArm64Graph = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.ClassPathType(
                        scope = ResolutionScope.COMPILE,
                        platforms = setOf(ResolutionPlatform.IOS_ARM64),
                        isTest = false,
                    ),
                    ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "shared",
                expected = """shared:COMPILE:IOS_ARM64
+--- shared:common:org.jetbrains.compose.foundation:foundation:1.7.3
|    \--- org.jetbrains.compose.foundation:foundation:1.7.3
|         \--- org.jetbrains.compose.foundation:foundation-uikitarm64:1.7.3
|              +--- org.jetbrains.compose.animation:animation:1.7.3
|              |    \--- org.jetbrains.compose.animation:animation-uikitarm64:1.7.3
|              |         +--- org.jetbrains.compose.animation:animation-core:1.7.3
|              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitarm64:1.7.3
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3
|              |         |         |    +--- androidx.annotation:annotation:1.8.0
|              |         |         |    |    \--- androidx.annotation:annotation-iosarm64:1.8.0
|              |         |         |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> ${UsedVersions.kotlinVersion}
|              |         |         |    |              \--- org.jetbrains.kotlin:kotlin-stdlib-common:${UsedVersions.kotlinVersion} (c)
|              |         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3
|              |         |         |    +--- androidx.collection:collection:1.4.0
|              |         |         |    |    \--- androidx.collection:collection-iosarm64:1.4.0
|              |         |         |    |         +--- androidx.annotation:annotation:1.7.0 -> 1.8.0 (*)
|              |         |         |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |         |         |    +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |    \--- org.jetbrains.kotlinx:atomicfu:0.23.2
|              |         |         |         \--- org.jetbrains.kotlinx:atomicfu-iosarm64:0.23.2
|              |         |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3
|              |         |         |    \--- org.jetbrains.compose.runtime:runtime-uikitarm64:1.7.3
|              |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|              |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|              |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-iosarm64:1.8.0
|              |         |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
|              |         |         |                   \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|              |         |         +--- org.jetbrains.compose.ui:ui:1.7.3
|              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitarm64:1.7.3
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-common:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-common-iosarm64:2.8.5
|              |         |         |         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    |         +--- org.jetbrains.kotlinx:atomicfu:0.17.0 -> 0.23.2 (*)
|              |         |         |         |    |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-runtime:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-runtime-iosarm64:2.8.5
|              |         |         |         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |    |         +--- androidx.lifecycle:lifecycle-common:2.8.5 (*)
|              |         |         |         |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|              |         |         |         |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4
|              |         |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitarm64:2.8.4
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4 (*)
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.11 -> 1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-viewmodel:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-viewmodel-iosarm64:2.8.5
|              |         |         |         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitarm64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitarm64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-uikitarm64:1.7.3
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.7.3
|              |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-uikit-uikitarm64:1.7.3
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitarm64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitarm64:1.7.3
|              |         |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.18
|              |         |         |         |              \--- org.jetbrains.skiko:skiko-iosarm64:0.8.18
|              |         |         |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitarm64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.18 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.7.3 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         \--- org.jetbrains.skiko:skiko:0.8.18 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3
|              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitarm64:1.7.3
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
|              +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              \--- org.jetbrains.skiko:skiko:0.8.18 (*)
+--- shared:common:org.jetbrains.compose.material3:material3:1.7.3
|    \--- org.jetbrains.compose.material3:material3:1.7.3
|         \--- org.jetbrains.compose.material3:material3-uikitarm64:1.7.3
|              +--- org.jetbrains.compose.animation:animation-core:1.7.3 (*)
|              +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
|              +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
|              +--- org.jetbrains.compose.material:material-icons-core:1.7.3
|              |    \--- org.jetbrains.compose.material:material-icons-core-uikitarm64:1.7.3
|              |         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              +--- org.jetbrains.compose.material:material-ripple:1.7.3
|              |    \--- org.jetbrains.compose.material:material-ripple-uikitarm64:1.7.3
|              |         +--- org.jetbrains.compose.animation:animation:1.7.3 (*)
|              |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
|              |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              \--- org.jetbrains.kotlinx:kotlinx-datetime:0.6.0
|                   \--- org.jetbrains.kotlinx:kotlinx-datetime-iosarm64:0.6.0
|                        +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
|                        |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-iosarm64:1.6.2
|                        |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> ${UsedVersions.kotlinVersion} (*)
|                        |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|                        \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
+--- shared:iosArm64:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
+--- shared:iosArm64:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
+--- shared:apple:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
+--- shared:apple:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
+--- shared:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
+--- shared:common:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
+--- shared:ios:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
+--- shared:ios:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
+--- shared:native:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
\--- shared:native:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
     \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
             """.trimIndent()
            )
        }

        // Subgraph for "org.jetbrains.kotlin:kotlin-stdlib" shows places referencing the dependency
        // of the exact effective version only (${UsedVersions.kotlinVersion}).
        // There are other paths to this dependency referencing another version of this dependency, those are skipped as well as same-version constraints.
        runBlocking {
            assertInsight(
                group = "org.jetbrains.compose.ui",
                module = "ui-graphics",
                graph = sharedModuleIosArm64Graph,
                expected = """shared:COMPILE:IOS_ARM64
+--- shared:common:org.jetbrains.compose.foundation:foundation:1.7.3
|    \--- org.jetbrains.compose.foundation:foundation:1.7.3
|         \--- org.jetbrains.compose.foundation:foundation-uikitarm64:1.7.3
|              +--- org.jetbrains.compose.animation:animation:1.7.3
|              |    \--- org.jetbrains.compose.animation:animation-uikitarm64:1.7.3
|              |         +--- org.jetbrains.compose.animation:animation-core:1.7.3
|              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitarm64:1.7.3
|              |         |         \--- org.jetbrains.compose.ui:ui:1.7.3
|              |         |              \--- org.jetbrains.compose.ui:ui-uikitarm64:1.7.3
|              |         |                   +--- org.jetbrains.compose.ui:ui-graphics:1.7.3
|              |         |                   \--- org.jetbrains.compose.ui:ui-text:1.7.3
|              |         |                        \--- org.jetbrains.compose.ui:ui-text-uikitarm64:1.7.3
|              |         |                             \--- org.jetbrains.compose.ui:ui-graphics:1.7.3
|              |         +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3
|              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitarm64:1.7.3
|              |         |         \--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              |         \--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              \--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
\--- shared:common:org.jetbrains.compose.material3:material3:1.7.3
     \--- org.jetbrains.compose.material3:material3:1.7.3
          \--- org.jetbrains.compose.material3:material3-uikitarm64:1.7.3
               +--- org.jetbrains.compose.animation:animation-core:1.7.3 (*)
               +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
               +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
               +--- org.jetbrains.compose.material:material-icons-core:1.7.3
               |    \--- org.jetbrains.compose.material:material-icons-core-uikitarm64:1.7.3
               |         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
               |         \--- org.jetbrains.compose.ui:ui-graphics:1.7.3
               +--- org.jetbrains.compose.material:material-ripple:1.7.3
               |    \--- org.jetbrains.compose.material:material-ripple-uikitarm64:1.7.3
               |         +--- org.jetbrains.compose.animation:animation:1.7.3 (*)
               |         \--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
               +--- org.jetbrains.compose.ui:ui-graphics:1.7.3
               \--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
                    """.trimIndent()
            )

            // Subgraph for "org.jetbrains.kotlin:kotlin-stdlib-common" shows all places referencing the dependency
            // since none of those places references the exact effective version (${UsedVersions.kotlinVersion}).
            // Also, the path to the constraint defining the effective version (${UsedVersions.kotlinVersion}) is also presented in a graph.
            assertInsight(
                group = "org.jetbrains.kotlin",
                module = "kotlin-stdlib-common",
                graph = sharedModuleIosArm64Graph,
                expected = """shared:COMPILE:IOS_ARM64
+--- shared:common:org.jetbrains.compose.foundation:foundation:1.7.3
|    \--- org.jetbrains.compose.foundation:foundation:1.7.3
|         \--- org.jetbrains.compose.foundation:foundation-uikitarm64:1.7.3
|              +--- org.jetbrains.compose.animation:animation:1.7.3
|              |    \--- org.jetbrains.compose.animation:animation-uikitarm64:1.7.3
|              |         +--- org.jetbrains.compose.animation:animation-core:1.7.3
|              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitarm64:1.7.3
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3
|              |         |         |    \--- org.jetbrains.compose.runtime:runtime-uikitarm64:1.7.3
|              |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|              |         |         |                   \--- org.jetbrains.kotlin:kotlin-stdlib-common:${UsedVersions.kotlinVersion} (c)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.7.3
|              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitarm64:1.7.3
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitarm64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitarm64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-uikitarm64:1.7.3
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitarm64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitarm64:1.7.3
|              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitarm64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3
|              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitarm64:1.7.3
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
|              +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
+--- shared:common:org.jetbrains.compose.material3:material3:1.7.3
|    \--- org.jetbrains.compose.material3:material3:1.7.3
|         \--- org.jetbrains.compose.material3:material3-uikitarm64:1.7.3
|              +--- org.jetbrains.compose.animation:animation-core:1.7.3 (*)
|              +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
|              +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
|              +--- org.jetbrains.compose.material:material-icons-core:1.7.3
|              |    \--- org.jetbrains.compose.material:material-icons-core-uikitarm64:1.7.3
|              |         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              +--- org.jetbrains.compose.material:material-ripple:1.7.3
|              |    \--- org.jetbrains.compose.material:material-ripple-uikitarm64:1.7.3
|              |         +--- org.jetbrains.compose.animation:animation:1.7.3 (*)
|              |         +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
|              |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              \--- org.jetbrains.kotlinx:kotlinx-datetime:0.6.0
|                   \--- org.jetbrains.kotlinx:kotlinx-datetime-iosarm64:0.6.0
|                        \--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
|                             \--- org.jetbrains.kotlinx:kotlinx-serialization-core-iosarm64:1.6.2
|                                  \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> ${UsedVersions.kotlinVersion} (*)
+--- shared:iosArm64:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
+--- shared:iosArm64:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
+--- shared:apple:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
+--- shared:apple:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
+--- shared:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
+--- shared:common:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
+--- shared:ios:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
+--- shared:ios:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
+--- shared:native:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
\--- shared:native:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
     \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
""".trimIndent()
            )

            // Assert that all dependencies "org.jetbrains.kotlin:kotlin-stdlib-common" have correct overriddenBy
            sharedModuleIosArm64Graph
                .distinctBfsSequence()
                .filterIsInstance<MavenDependencyNode>()
                .filter { it.group == "org.jetbrains.kotlin" && it.module == "kotlin-stdlib-common" }
                .forEach {
                    if (it.originalVersion() == it.resolvedVersion()) {
                        assertNull(it.overriddenBy)
                    } else {
                        assertNotNull(
                            it.overriddenBy,
                            "Expected non-null 'overriddenBy' since ${it.resolvedVersion()} doesn't match ${it.originalVersion()}"
                        )
                        val constraintNode = it.overriddenBy.singleOrNull() as? MavenDependencyConstraintNode
                        assertNotNull(
                            constraintNode,
                            "Expected the only dependency constraint node in 'overriddenBy', but found ${
                                it.overriddenBy.map { it.key }.toSet()
                            }"
                        )
                        assertEquals(
                            constraintNode.key.name, "org.jetbrains.kotlin:kotlin-stdlib-common",
                            "Unexpected constraint ${constraintNode.key}"
                        )
                    }
                }

            assertInsight(
                group = "org.jetbrains.kotlinx",
                module = "kotlinx-coroutines-core",
                graph = sharedModuleIosArm64Graph,
                expected = """
shared:COMPILE:IOS_ARM64
+--- shared:common:org.jetbrains.compose.foundation:foundation:1.7.3
|    \--- org.jetbrains.compose.foundation:foundation:1.7.3
|         \--- org.jetbrains.compose.foundation:foundation-uikitarm64:1.7.3
|              +--- org.jetbrains.compose.animation:animation:1.7.3
|              |    \--- org.jetbrains.compose.animation:animation-uikitarm64:1.7.3
|              |         +--- org.jetbrains.compose.animation:animation-core:1.7.3
|              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitarm64:1.7.3
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3
|              |         |         |    \--- org.jetbrains.compose.runtime:runtime-uikitarm64:1.7.3
|              |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|              |         |         +--- org.jetbrains.compose.ui:ui:1.7.3
|              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitarm64:1.7.3
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4
|              |         |         |         |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4
|              |         |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitarm64:2.8.4
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|              |         |         |         |         \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitarm64:1.7.3
|              |         |         |         |         \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitarm64:1.7.3
|              |         |         |         |         \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitarm64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitarm64:1.7.3
|              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         |         \--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.18
|              |         |         |         |              \--- org.jetbrains.skiko:skiko-iosarm64:0.8.18
|              |         |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitarm64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.18 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|              |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|              |         |         |         \--- org.jetbrains.skiko:skiko:0.8.18 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|              |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|              |         +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3
|              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitarm64:1.7.3
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         \--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              |         \--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
|              +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
|              \--- org.jetbrains.skiko:skiko:0.8.18 (*)
+--- shared:common:org.jetbrains.compose.material3:material3:1.7.3
|    \--- org.jetbrains.compose.material3:material3:1.7.3
|         \--- org.jetbrains.compose.material3:material3-uikitarm64:1.7.3
|              +--- org.jetbrains.compose.animation:animation-core:1.7.3 (*)
|              +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
|              +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
|              +--- org.jetbrains.compose.material:material-icons-core:1.7.3
|              |    \--- org.jetbrains.compose.material:material-icons-core-uikitarm64:1.7.3
|              |         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|              |         \--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|              +--- org.jetbrains.compose.material:material-ripple:1.7.3
|              |    \--- org.jetbrains.compose.material:material-ripple-uikitarm64:1.7.3
|              |         +--- org.jetbrains.compose.animation:animation:1.7.3 (*)
|              |         +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
|              |         \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|              \--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
+--- shared:iosArm64:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
+--- shared:apple:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
+--- shared:common:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
+--- shared:ios:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
\--- shared:native:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
     \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
""".trimIndent()
            )
        }
    }

    private fun assertInsight(group: String, module: String, graph: DependencyNode, expected: String) {
        with(moduleDependenciesResolver) {
            val subGraph = dependencyInsight(group, module, graph)
            assertEquals(expected, subGraph)
        }
    }
}
