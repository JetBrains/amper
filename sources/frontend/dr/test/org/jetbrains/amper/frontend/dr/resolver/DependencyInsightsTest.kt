/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyConstraintNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.originalVersion
import org.jetbrains.amper.dependency.resolution.resolvedVersion
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DependencyInsightsTest : BaseModuleDrTest() {
    @Test
    fun `test sync empty jvm module`() {
        val aom = getTestProjectModel("jvm-empty", testDataRoot)

        kotlin.test.assertEquals(aom.modules[0].fragments.map { it.name }.toSet(),  setOf("jvm", "jvmTest"), "")

        val jvmEmptyModuleGraph = runBlocking {
            doTest(
                aom,
                resolutionInput = ResolutionInput(DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL) ,
                module = "jvm-empty",
                expected = """
                    |module:jvm-empty
                    |+--- jvm-empty:jvm:org.jetbrains.kotlin:kotlin-stdlib:2.0.21, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
                    ||         \--- org.jetbrains:annotations:13.0
                    |+--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-stdlib:2.0.21, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21 (*)
                    |\--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:2.0.21, implicit
                    |     \--- org.jetbrains.kotlin:kotlin-test-junit:2.0.21
                    |          +--- org.jetbrains.kotlin:kotlin-test:2.0.21
                    |          |    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21 (*)
                    |          \--- junit:junit:4.13.2
                    |               \--- org.hamcrest:hamcrest-core:1.3"""
                    .trimMargin()
            )
        }

        runBlocking {
            assertInsight(
                group = "org.jetbrains.kotlin",
                module = "kotlin-stdlib",
                graph = jvmEmptyModuleGraph,
                expected = """
                    |module:jvm-empty
                    |+--- jvm-empty:jvm:org.jetbrains.kotlin:kotlin-stdlib:2.0.21, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
                    |+--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-stdlib:2.0.21, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
                    |\--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:2.0.21, implicit
                    |     \--- org.jetbrains.kotlin:kotlin-test-junit:2.0.21
                    |          \--- org.jetbrains.kotlin:kotlin-test:2.0.21
                    |               \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21""".trimMargin()
                )
            assertInsight(
                group = "org.hamcrest",
                module = "hamcrest-core",
                graph = jvmEmptyModuleGraph,
                expected = """
                    |module:jvm-empty
                    |\--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:2.0.21, implicit
                    |     \--- org.jetbrains.kotlin:kotlin-test-junit:2.0.21
                    |          \--- junit:junit:4.13.2
                    |               \--- org.hamcrest:hamcrest-core:1.3""".trimMargin()
            )
            assertInsight(
                group = "org.jetbrains.kotlin",
                module = "kotlin-test-junit",
                graph = jvmEmptyModuleGraph,
                expected = """
                    |module:jvm-empty
                    |\--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:2.0.21, implicit
                    |     \--- org.jetbrains.kotlin:kotlin-test-junit:2.0.21""".trimMargin()
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
                        isTest = false),
                    ResolutionDepth.GRAPH_FULL
                ),
                module = "shared",
                expected = """shared:COMPILE:IOS_ARM64
                |+--- shared:common:org.jetbrains.compose.foundation:foundation:1.6.10
                ||    \--- org.jetbrains.compose.foundation:foundation:1.6.10
                ||         \--- org.jetbrains.compose.foundation:foundation-uikitarm64:1.6.10
                ||              +--- org.jetbrains.compose.animation:animation:1.6.10
                ||              |    \--- org.jetbrains.compose.animation:animation-uikitarm64:1.6.10
                ||              |         +--- org.jetbrains.compose.animation:animation-core:1.6.10
                ||              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitarm64:1.6.10
                ||              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10
                ||              |         |         |    \--- androidx.annotation:annotation:1.8.0
                ||              |         |         |         \--- androidx.annotation:annotation-iosarm64:1.8.0
                ||              |         |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 2.0.21
                ||              |         |         |                   \--- org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21 (c)
                ||              |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10
                ||              |         |         |    \--- androidx.collection:collection:1.4.0
                ||              |         |         |         \--- androidx.collection:collection-iosarm64:1.4.0
                ||              |         |         |              +--- androidx.annotation:annotation:1.7.0 -> 1.8.0 (*)
                ||              |         |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
                ||              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10
                ||              |         |         |    \--- org.jetbrains.compose.runtime:runtime-uikitarm64:1.6.10
                ||              |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
                ||              |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2
                ||              |         |         |         |    \--- org.jetbrains.kotlinx:atomicfu-iosarm64:0.23.2
                ||              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
                ||              |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
                ||              |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-iosarm64:1.8.0
                ||              |         |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
                ||              |         |         |                   \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
                ||              |         |         +--- org.jetbrains.compose.ui:ui:1.6.10
                ||              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitarm64:1.6.10
                ||              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0
                ||              |         |         |         |    \--- androidx.lifecycle:lifecycle-common:2.8.0
                ||              |         |         |         |         \--- androidx.lifecycle:lifecycle-common-iosarm64:2.8.0
                ||              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
                ||              |         |         |         |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
                ||              |         |         |         |              +--- org.jetbrains.kotlinx:atomicfu:0.17.0 -> 0.23.2 (*)
                ||              |         |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
                ||              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0
                ||              |         |         |         |    \--- androidx.lifecycle:lifecycle-runtime:2.8.0
                ||              |         |         |         |         \--- androidx.lifecycle:lifecycle-runtime-iosarm64:2.8.0
                ||              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
                ||              |         |         |         |              +--- androidx.lifecycle:lifecycle-common:2.8.0 (*)
                ||              |         |         |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
                ||              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.0
                ||              |         |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitarm64:2.8.0
                ||              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0 (*)
                ||              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0 (*)
                ||              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                ||              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
                ||              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.0
                ||              |         |         |         |    \--- androidx.lifecycle:lifecycle-viewmodel:2.8.0
                ||              |         |         |         |         \--- androidx.lifecycle:lifecycle-viewmodel-iosarm64:2.8.0
                ||              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
                ||              |         |         |         |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
                ||              |         |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
                ||              |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
                ||              |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
                ||              |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                ||              |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10
                ||              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitarm64:1.6.10
                ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                ||              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10
                ||              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitarm64:1.6.10
                ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10
                ||              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-uikitarm64:1.6.10
                ||              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.6.10
                ||              |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-uikit-uikitarm64:1.6.10
                ||              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10
                ||              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitarm64:1.6.10
                ||              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10
                ||              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitarm64:1.6.10
                ||              |         |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
                ||              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                ||              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
                ||              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                ||              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4
                ||              |         |         |         |              \--- org.jetbrains.skiko:skiko-iosarm64:0.8.4
                ||              |         |         |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
                ||              |         |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
                ||              |         |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
                ||              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.6.10
                ||              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitarm64:1.6.10
                ||              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                ||              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              |         |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
                ||              |         |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
                ||              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
                ||              |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.6.10 (*)
                ||              |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
                ||              |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                ||              |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
                ||              |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
                ||              |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
                ||              |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
                ||              |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                ||              |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
                ||              |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
                ||              |         +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10
                ||              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitarm64:1.6.10
                ||              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
                ||              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                ||              |         |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
                ||              |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                ||              |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                ||              |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
                ||              |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
                ||              |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                ||              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
                ||              +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
                ||              +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
                ||              +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                ||              +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
                ||              +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
                ||              +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                ||              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
                ||              \--- org.jetbrains.skiko:skiko:0.8.4 (*)
                |+--- shared:common:org.jetbrains.compose.material3:material3:1.6.10
                ||    \--- org.jetbrains.compose.material3:material3:1.6.10
                ||         \--- org.jetbrains.compose.material3:material3-uikitarm64:1.6.10
                ||              +--- org.jetbrains.compose.animation:animation-core:1.6.10 (*)
                ||              +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
                ||              +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
                ||              +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
                ||              +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
                ||              +--- org.jetbrains.compose.material:material-icons-core:1.6.10
                ||              |    \--- org.jetbrains.compose.material:material-icons-core-uikitarm64:1.6.10
                ||              |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
                ||              |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
                ||              |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
                ||              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              +--- org.jetbrains.compose.material:material-ripple:1.6.10
                ||              |    \--- org.jetbrains.compose.material:material-ripple-uikitarm64:1.6.10
                ||              |         +--- org.jetbrains.compose.animation:animation:1.6.10 (*)
                ||              |         +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
                ||              |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                ||              |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                ||              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                ||              +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
                ||              +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
                ||              +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                ||              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                ||              +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
                ||              \--- org.jetbrains.kotlinx:kotlinx-datetime:0.5.0
                ||                   \--- org.jetbrains.kotlinx:kotlinx-datetime-iosarm64:0.5.0
                ||                        +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
                ||                        |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-iosarm64:1.6.2
                ||                        |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> 2.0.21
                ||                        |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
                ||                        \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
                |+--- shared:iosArm64:org.jetbrains.kotlin:kotlin-stdlib:2.0.21, implicit
                ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
                |+--- shared:iosArm64:org.jetbrains.compose.runtime:runtime:1.6.10, implicit
                ||    \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                |+--- shared:common:org.jetbrains.kotlin:kotlin-stdlib:2.0.21, implicit
                ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
                |+--- shared:common:org.jetbrains.compose.runtime:runtime:1.6.10, implicit
                ||    \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                |+--- shared:ios:org.jetbrains.kotlin:kotlin-stdlib:2.0.21, implicit
                ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
                |\--- shared:ios:org.jetbrains.compose.runtime:runtime:1.6.10, implicit
                |     \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
             """.trimMargin()
            )
        }

        // Subgraph for "org.jetbrains.kotlin:kotlin-stdlib" shows places referencing the dependency
        // of the exact effective version only (2.0.21).
        // There are other paths to this dependency referencing another version of this dependency, those are skipped as well as same-version constraints.
        runBlocking {
            assertInsight(
                group = "org.jetbrains.kotlin",
                module = "kotlin-stdlib",
                graph = sharedModuleIosArm64Graph,
                expected = """
                    |shared:COMPILE:IOS_ARM64
                    |+--- shared:iosArm64:org.jetbrains.kotlin:kotlin-stdlib:2.0.21, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
                    |+--- shared:common:org.jetbrains.kotlin:kotlin-stdlib:2.0.21, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
                    |\--- shared:ios:org.jetbrains.kotlin:kotlin-stdlib:2.0.21, implicit
                    |     \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
                    """.trimMargin()
            )

            // Subgraph for "org.jetbrains.kotlin:kotlin-stdlib-common" shows all places referencing the dependency
            // since none of those places references the exact effective version (2.0.21).
            // Also, the path to the constraint defining the effective version (2.0.21) is also presented in a graph.
            assertInsight(
                group = "org.jetbrains.kotlin",
                module = "kotlin-stdlib-common",
                graph = sharedModuleIosArm64Graph,
                expected = """
                    |shared:COMPILE:IOS_ARM64
                    |+--- shared:common:org.jetbrains.compose.foundation:foundation:1.6.10
                    ||    \--- org.jetbrains.compose.foundation:foundation:1.6.10
                    ||         \--- org.jetbrains.compose.foundation:foundation-uikitarm64:1.6.10
                    ||              +--- org.jetbrains.compose.animation:animation:1.6.10
                    ||              |    \--- org.jetbrains.compose.animation:animation-uikitarm64:1.6.10
                    ||              |         +--- org.jetbrains.compose.animation:animation-core:1.6.10
                    ||              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitarm64:1.6.10
                    ||              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10
                    ||              |         |         |    \--- org.jetbrains.compose.runtime:runtime-uikitarm64:1.6.10
                    ||              |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              |         |         +--- org.jetbrains.compose.ui:ui:1.6.10
                    ||              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitarm64:1.6.10
                    ||              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.0
                    ||              |         |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitarm64:2.8.0
                    ||              |         |         |         |         \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10
                    ||              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitarm64:1.6.10
                    ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10
                    ||              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitarm64:1.6.10
                    ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10
                    ||              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-uikitarm64:1.6.10
                    ||              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10
                    ||              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitarm64:1.6.10
                    ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10
                    ||              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitarm64:1.6.10
                    ||              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
                    ||              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                    ||              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                    ||              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.6.10
                    ||              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitarm64:1.6.10
                    ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                    ||              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
                    ||              |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                    ||              |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
                    ||              |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                    ||              |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              |         +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10
                    ||              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitarm64:1.6.10
                    ||              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
                    ||              |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                    ||              |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
                    ||              |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
                    ||              |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                    ||              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                    ||              \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    |+--- shared:common:org.jetbrains.compose.material3:material3:1.6.10
                    ||    \--- org.jetbrains.compose.material3:material3:1.6.10
                    ||         \--- org.jetbrains.compose.material3:material3-uikitarm64:1.6.10
                    ||              +--- org.jetbrains.compose.animation:animation-core:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.material:material-icons-core:1.6.10
                    ||              |    \--- org.jetbrains.compose.material:material-icons-core-uikitarm64:1.6.10
                    ||              |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
                    ||              |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
                    ||              |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
                    ||              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              +--- org.jetbrains.compose.material:material-ripple:1.6.10
                    ||              |    \--- org.jetbrains.compose.material:material-ripple-uikitarm64:1.6.10
                    ||              |         +--- org.jetbrains.compose.animation:animation:1.6.10 (*)
                    ||              |         +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
                    ||              |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                    ||              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
                    ||              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
                    ||              \--- org.jetbrains.kotlinx:kotlinx-datetime:0.5.0
                    ||                   \--- org.jetbrains.kotlinx:kotlinx-datetime-iosarm64:0.5.0
                    ||                        \--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
                    ||                             \--- org.jetbrains.kotlinx:kotlinx-serialization-core-iosarm64:1.6.2
                    ||                                  \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> 2.0.21
                    |+--- shared:iosArm64:org.jetbrains.kotlin:kotlin-stdlib:2.0.21, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
                    ||         \--- org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21 (c)
                    |+--- shared:iosArm64:org.jetbrains.compose.runtime:runtime:1.6.10, implicit
                    ||    \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    |+--- shared:common:org.jetbrains.kotlin:kotlin-stdlib:2.0.21, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21 (*)
                    |+--- shared:common:org.jetbrains.compose.runtime:runtime:1.6.10, implicit
                    ||    \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    |+--- shared:ios:org.jetbrains.kotlin:kotlin-stdlib:2.0.21, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21 (*)
                    |\--- shared:ios:org.jetbrains.compose.runtime:runtime:1.6.10, implicit
                    |     \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    """.trimMargin()
            )

            // Assert that all nodes representing different versions
            // of the library "org.jetbrains.kotlin:kotlin-stdlib-common" in the graph have correct overriddenBy
            sharedModuleIosArm64Graph
                .distinctBfsSequence()
                .filterIsInstance<MavenDependencyNode>()
                .filter { it.group == "org.jetbrains.kotlin" && it.module == "kotlin-stdlib-common"  }
                .forEach {
                    if (it.originalVersion() == it.resolvedVersion()) {
                        assertNull(it.overriddenBy)
                    } else {
                        assertNotNull(it.overriddenBy,
                            "Expected non-null 'overriddenBy' since ${it.resolvedVersion()} doesn't match ${it.originalVersion()}")
                        val constraintNode = it.overriddenBy.singleOrNull() as? MavenDependencyConstraintNode
                        assertNotNull(constraintNode,
                            "Expected the only dependency constraint node in 'overriddenBy', but found ${ it.overriddenBy.map { it::class.simpleName }.toSet() }")
                        kotlin.test.assertEquals(constraintNode.key.name, "org.jetbrains.kotlin:kotlin-stdlib-common",
                            "Unexpected constraint ${constraintNode.key}")
                    }
                }

            assertInsight(
                group = "org.jetbrains.kotlinx",
                module = "kotlinx-coroutines-core",
                graph = sharedModuleIosArm64Graph,
                expected = """
                    |shared:COMPILE:IOS_ARM64
                    |+--- shared:common:org.jetbrains.compose.foundation:foundation:1.6.10
                    ||    \--- org.jetbrains.compose.foundation:foundation:1.6.10
                    ||         \--- org.jetbrains.compose.foundation:foundation-uikitarm64:1.6.10
                    ||              +--- org.jetbrains.compose.animation:animation:1.6.10
                    ||              |    \--- org.jetbrains.compose.animation:animation-uikitarm64:1.6.10
                    ||              |         +--- org.jetbrains.compose.animation:animation-core:1.6.10
                    ||              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitarm64:1.6.10
                    ||              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10
                    ||              |         |         |    \--- org.jetbrains.compose.runtime:runtime-uikitarm64:1.6.10
                    ||              |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
                    ||              |         |         +--- org.jetbrains.compose.ui:ui:1.6.10
                    ||              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitarm64:1.6.10
                    ||              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.0
                    ||              |         |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitarm64:2.8.0
                    ||              |         |         |         |         \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10
                    ||              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitarm64:1.6.10
                    ||              |         |         |         |         \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10
                    ||              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitarm64:1.6.10
                    ||              |         |         |         |         \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10
                    ||              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitarm64:1.6.10
                    ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10
                    ||              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitarm64:1.6.10
                    ||              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         |         |         \--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
                    ||              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4
                    ||              |         |         |         |              \--- org.jetbrains.skiko:skiko-iosarm64:0.8.4
                    ||              |         |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
                    ||              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.6.10
                    ||              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitarm64:1.6.10
                    ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
                    ||              |         |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
                    ||              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
                    ||              |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
                    ||              |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
                    ||              |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
                    ||              |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
                    ||              |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
                    ||              |         +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10
                    ||              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitarm64:1.6.10
                    ||              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         |         \--- org.jetbrains.compose.ui:ui:1.6.10 (*)
                    ||              |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
                    ||              |         \--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
                    ||              \--- org.jetbrains.skiko:skiko:0.8.4 (*)
                    |+--- shared:common:org.jetbrains.compose.material3:material3:1.6.10
                    ||    \--- org.jetbrains.compose.material3:material3:1.6.10
                    ||         \--- org.jetbrains.compose.material3:material3-uikitarm64:1.6.10
                    ||              +--- org.jetbrains.compose.animation:animation-core:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.material:material-icons-core:1.6.10
                    ||              |    \--- org.jetbrains.compose.material:material-icons-core-uikitarm64:1.6.10
                    ||              |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
                    ||              |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
                    ||              |         \--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.material:material-ripple:1.6.10
                    ||              |    \--- org.jetbrains.compose.material:material-ripple-uikitarm64:1.6.10
                    ||              |         +--- org.jetbrains.compose.animation:animation:1.6.10 (*)
                    ||              |         +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
                    ||              |         \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    ||              +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
                    ||              \--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
                    |+--- shared:iosArm64:org.jetbrains.compose.runtime:runtime:1.6.10, implicit
                    ||    \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    |+--- shared:common:org.jetbrains.compose.runtime:runtime:1.6.10, implicit
                    ||    \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    |\--- shared:ios:org.jetbrains.compose.runtime:runtime:1.6.10, implicit
                    |     \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
                    """.trimMargin()
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