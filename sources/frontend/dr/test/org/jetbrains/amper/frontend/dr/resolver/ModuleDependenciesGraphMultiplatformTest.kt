/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

/**
 * Resolved module dependencies graph for the test project 'compose-multiplatform' is almost identical to what Gradle resolves*.
 * Be careful: changing of the expected result might rather highlight
 * the error introduced to resolution logic than its improvement while DR evolving.
 *
 * Known sources of differences between Amper and Gradle resolution logic:
 *
 * 1. Gradle includes dependency on 'org.jetbrains.compose.components:components-resources' unconditionally,
 *    while Amper adds this dependency in case module does have 'compose' resources only.
 * 2. Amper resolves runtime version of a library on IDE sync.
 *    This might cause a difference with the graph produced by Gradle.
 *    It will be fixed in the nearest future (as soon as Amper IDE plugin started calling
 *    CLI for running application instead of reusing module classpath from the Workspace model)
 */
class ModuleDependenciesGraphMultiplatformTest : BaseModuleDrTest() {

    @Test
    fun `test sync empty jvm module`() {
        val aom = getTestProjectModel("jvm-empty", testDataRoot)

        assertEquals(
            setOf("common", "commonTest", "jvm", "jvmTest"),
            aom.modules[0].fragments.map { it.name }.toSet(),
            ""
        )


        val jvmTestFragmentDeps = runBlocking {
            doTest(
                aom,
                resolutionInput = ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "jvm-empty",
                expected = """module:jvm-empty
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
     \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion} (*)"""
            )
        }

        runBlocking {
            downloadAndAssertFiles(
                files = """
                |annotations-13.0.jar
                |hamcrest-core-1.3.jar
                |junit-4.13.2.jar
                |kotlin-stdlib-${UsedVersions.kotlinVersion}.jar
                |kotlin-test-${UsedVersions.kotlinVersion}.jar
                |kotlin-test-junit-${UsedVersions.kotlinVersion}.jar
                """.trimMargin(),
                jvmTestFragmentDeps
            )
        }
    }

    @Test
    fun `test shared@ios dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val sharedIosFragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "shared",
                fragment = "ios",
                expected = """Fragment 'shared.ios' dependencies
+--- shared:ios:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
+--- shared:ios:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3
|         +--- org.jetbrains.kotlinx:atomicfu:0.23.2
|         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion}
|         |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|         |    +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
|         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|         \--- org.jetbrains.compose.collection-internal:collection:1.7.3
|              +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3
|              |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
+--- shared:ios:org.jetbrains.compose.foundation:foundation:1.7.3
|    \--- org.jetbrains.compose.foundation:foundation:1.7.3
|         +--- org.jetbrains.compose.animation:animation:1.7.3
|         |    +--- org.jetbrains.compose.animation:animation-core:1.7.3
|         |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|         |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    +--- org.jetbrains.compose.ui:ui:1.7.3
|         |    |    |    +--- org.jetbrains.compose.runtime:runtime-saveable:1.7.3
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.7.3
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    |    |    \--- org.jetbrains.compose.ui:ui-util:1.7.3
|         |    |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |         \--- org.jetbrains.compose.ui:ui-uikit:1.7.3
|         |    |    |    +--- org.jetbrains.compose.ui:ui-graphics:1.7.3
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.7.3
|         |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|         |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         |    |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.skiko:skiko:0.8.18
|         |    |    |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|         |    |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|         |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-text:1.7.3
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.skiko:skiko:0.8.18 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime-saveable:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|         |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.skiko:skiko:0.8.18 (*)
|         |    |    |    +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|         |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|         |    |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|         |    |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|         |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4
|         |    |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.11 -> 1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4
|         |    |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|         |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    \--- org.jetbrains.compose.ui:ui-uikit:1.7.3
|         |    |    +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|         |    |    +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         |    |    \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3
|         |    |    +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|         |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|         |    +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|         |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|         |    \--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|         +--- org.jetbrains.skiko:skiko:0.8.18 (*)
|         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|         +--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
|         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
|         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
\--- shared:ios:org.jetbrains.compose.material3:material3:1.7.3
     \--- org.jetbrains.compose.material3:material3:1.7.3
          +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
          +--- org.jetbrains.compose.material:material-icons-core:1.7.3
          |    +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
          |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
          |    +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
          |    \--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
          +--- org.jetbrains.compose.material:material-ripple:1.7.3
          |    +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
          |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
          |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
          |    +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
          |    +--- org.jetbrains.compose.animation:animation:1.7.3 (*)
          |    \--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
          +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
          +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
          +--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
          +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
          +--- org.jetbrains.compose.animation:animation-core:1.7.3 (*)
          +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
          +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
          +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
          +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
          +--- org.jetbrains.kotlinx:kotlinx-datetime:0.6.0
          |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
          |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
          |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> ${UsedVersions.kotlinVersion} (*)
          |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
          \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
             """.trimIndent()
            )
        }
        assertFiles(
            files = """
                |animation-commonMain-1.7.3.klib
                |animation-core-commonMain-1.7.3.klib
                |animation-core-jbMain-1.7.3.klib
                |animation-core-jsNativeMain-1.7.3.klib
                |animation-core-uikitMain-1.7.3.klib
                |animation-jsNativeMain-1.7.3.klib
                |animation-nativeMain-1.7.3.klib
                |annotation-commonMain-1.7.3.klib
                |annotation-nonJvmMain-1.7.3.klib
                |atomicfu-commonMain-0.23.2.klib
                |atomicfu-nativeMain-0.23.2.klib
                |collection-commonMain-1.7.3.klib
                |collection-jbMain-1.7.3.klib
                |collection-jsNativeMain-1.7.3.klib
                |foundation-commonMain-1.7.3.klib
                |foundation-darwinMain-1.7.3.klib
                |foundation-jsNativeMain-1.7.3.klib
                |foundation-layout-commonMain-1.7.3.klib
                |foundation-layout-jsNativeMain-1.7.3.klib
                |foundation-layout-skikoMain-1.7.3.klib
                |foundation-layout-uikitMain-1.7.3.klib
                |foundation-nativeMain-1.7.3.klib
                |foundation-skikoMain-1.7.3.klib
                |foundation-uikitMain-1.7.3.klib
                |kotlin-stdlib-commonMain-${UsedVersions.kotlinVersion}.klib
                |kotlinx-coroutines-core-commonMain-1.8.0.klib
                |kotlinx-coroutines-core-concurrentMain-1.8.0.klib
                |kotlinx-coroutines-core-nativeDarwinMain-1.8.0.klib
                |kotlinx-coroutines-core-nativeMain-1.8.0.klib
                |kotlinx-datetime-commonMain-0.6.0.klib
                |kotlinx-datetime-darwinMain-0.6.0.klib
                |kotlinx-datetime-nativeMain-0.6.0.klib
                |kotlinx-datetime-tzdbOnFilesystemMain-0.6.0.klib
                |kotlinx-datetime-tzfileMain-0.6.0.klib
                |kotlinx-serialization-core-commonMain-1.6.2.klib
                |kotlinx-serialization-core-nativeMain-1.6.2.klib
                |lifecycle-common-commonMain-2.8.4.klib
                |lifecycle-common-nonJvmMain-2.8.4.klib
                |lifecycle-runtime-commonMain-2.8.4.klib
                |lifecycle-runtime-compose-commonMain-2.8.4.klib
                |lifecycle-runtime-compose-nonAndroidMain-2.8.4.klib
                |lifecycle-runtime-nativeMain-2.8.4.klib
                |lifecycle-runtime-nonJvmMain-2.8.4.klib
                |lifecycle-viewmodel-commonMain-2.8.4.klib
                |lifecycle-viewmodel-nativeMain-2.8.4.klib
                |lifecycle-viewmodel-nonJvmMain-2.8.4.klib
                |material-icons-core-commonMain-1.7.3.klib
                |material-ripple-commonMain-1.7.3.klib
                |material-ripple-jbMain-1.7.3.klib
                |material3-commonMain-1.7.3.klib
                |material3-darwinMain-1.7.3.klib
                |material3-jsNativeMain-1.7.3.klib
                |material3-nativeMain-1.7.3.klib
                |material3-nonJvmMain-1.7.3.klib
                |material3-skikoMain-1.7.3.klib
                |runtime-commonMain-1.7.3.klib
                |runtime-darwinMain-1.7.3.klib
                |runtime-jbMain-1.7.3.klib
                |runtime-jsNativeMain-1.7.3.klib
                |runtime-nativeMain-1.7.3.klib
                |runtime-posixMain-1.7.3.klib
                |runtime-saveable-commonMain-1.7.3.klib
                |runtime-uikitMain-1.7.3.klib
                |skiko-commonMain-0.8.18.klib
                |skiko-darwinMain-0.8.18.klib
                |skiko-iosMain-0.8.18.klib
                |skiko-nativeJsMain-0.8.18.klib
                |skiko-nativeMain-0.8.18.klib
                |skiko-uikitMain-0.8.18.klib
                |ui-commonMain-1.7.3.klib
                |ui-darwinMain-1.7.3.klib
                |ui-geometry-commonMain-1.7.3.klib
                |ui-graphics-commonMain-1.7.3.klib
                |ui-graphics-jsNativeMain-1.7.3.klib
                |ui-graphics-nativeMain-1.7.3.klib
                |ui-graphics-skikoExcludingWebMain-1.7.3.klib
                |ui-graphics-skikoMain-1.7.3.klib
                |ui-jsNativeMain-1.7.3.klib
                |ui-nativeMain-1.7.3.klib
                |ui-skikoMain-1.7.3.klib
                |ui-text-commonMain-1.7.3.klib
                |ui-text-darwinMain-1.7.3.klib
                |ui-text-jsNativeMain-1.7.3.klib
                |ui-text-nativeMain-1.7.3.klib
                |ui-text-skikoMain-1.7.3.klib
                |ui-uikit-uikitMain-1.7.3.klib
                |ui-uikitMain-1.7.3.klib
                |ui-unit-commonMain-1.7.3.klib
                |ui-unit-jbMain-1.7.3.klib
                |ui-unit-jsNativeMain-1.7.3.klib
                |ui-util-commonMain-1.7.3.klib
                |ui-util-nonJvmMain-1.7.3.klib
                |ui-util-uikitMain-1.7.3.klib
                """.trimMargin(),
            sharedIosFragmentDeps
        )
    }

    @Test
    fun `test shared@iosX64 dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)
        val iosAppIosX64FragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "shared",
                fragment = "iosX64",
                expected = """Fragment 'shared.iosX64' dependencies
+--- shared:iosX64:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|         \--- org.jetbrains.kotlin:kotlin-stdlib-common:${UsedVersions.kotlinVersion} (c)
+--- shared:iosX64:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3
|         \--- org.jetbrains.compose.runtime:runtime-uikitx64:1.7.3
|              +--- org.jetbrains.compose.collection-internal:collection:1.7.3
|              |    +--- androidx.collection:collection:1.4.0
|              |    |    \--- androidx.collection:collection-iosx64:1.4.0
|              |    |         +--- androidx.annotation:annotation:1.7.0 -> 1.8.0
|              |    |         |    \--- androidx.annotation:annotation-iosx64:1.8.0
|              |    |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> ${UsedVersions.kotlinVersion}
|              |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |    +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3
|              |    |    +--- androidx.annotation:annotation:1.8.0 (*)
|              |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |    \--- org.jetbrains.kotlinx:atomicfu:0.23.2
|              |         \--- org.jetbrains.kotlinx:atomicfu-iosx64:0.23.2
|              |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-iosx64:1.8.0
|                        +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
|                        \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
+--- shared:iosX64:org.jetbrains.compose.foundation:foundation:1.7.3
|    \--- org.jetbrains.compose.foundation:foundation:1.7.3
|         \--- org.jetbrains.compose.foundation:foundation-uikitx64:1.7.3
|              +--- org.jetbrains.compose.animation:animation:1.7.3
|              |    \--- org.jetbrains.compose.animation:animation-uikitx64:1.7.3
|              |         +--- org.jetbrains.compose.animation:animation-core:1.7.3
|              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitx64:1.7.3
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.7.3
|              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitx64:1.7.3
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-common:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-common-iosx64:2.8.5
|              |         |         |         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    |         +--- org.jetbrains.kotlinx:atomicfu:0.17.0 -> 0.23.2 (*)
|              |         |         |         |    |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-runtime:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-runtime-iosx64:2.8.5
|              |         |         |         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |    |         +--- androidx.lifecycle:lifecycle-common:2.8.5 (*)
|              |         |         |         |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|              |         |         |         |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4
|              |         |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitx64:2.8.4
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4 (*)
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.11 -> 1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-viewmodel:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-viewmodel-iosx64:2.8.5
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
|              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitx64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitx64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-uikitx64:1.7.3
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.7.3
|              |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-uikit-uikitx64:1.7.3
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitx64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitx64:1.7.3
|              |         |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.18
|              |         |         |         |              \--- org.jetbrains.skiko:skiko-iosx64:0.8.18
|              |         |         |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitx64:1.7.3
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
|              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitx64:1.7.3
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
\--- shared:iosX64:org.jetbrains.compose.material3:material3:1.7.3
     \--- org.jetbrains.compose.material3:material3:1.7.3
          \--- org.jetbrains.compose.material3:material3-uikitx64:1.7.3
               +--- org.jetbrains.compose.animation:animation-core:1.7.3 (*)
               +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
               +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
               +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
               +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
               +--- org.jetbrains.compose.material:material-icons-core:1.7.3
               |    \--- org.jetbrains.compose.material:material-icons-core-uikitx64:1.7.3
               |         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
               |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
               |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
               |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
               +--- org.jetbrains.compose.material:material-ripple:1.7.3
               |    \--- org.jetbrains.compose.material:material-ripple-uikitx64:1.7.3
               |         +--- org.jetbrains.compose.animation:animation:1.7.3 (*)
               |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
               |         +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
               |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
               |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
               |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
               +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
               +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
               +--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
               +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
               +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
               +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
               \--- org.jetbrains.kotlinx:kotlinx-datetime:0.6.0
                    \--- org.jetbrains.kotlinx:kotlinx-datetime-iosx64:0.6.0
                         +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
                         |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-iosx64:1.6.2
                         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> ${UsedVersions.kotlinVersion} (*)
                         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
                         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
                  """.trimIndent()
            )
        }

        assertFiles(
            files = """
                |animation-core-uikitx64-1.7.3.klib
                |animation-uikitx64-1.7.3.klib
                |annotation-iosx64-1.8.0.klib
                |atomicfu-iosx64-0.23.2-cinterop-interop.klib
                |atomicfu-iosx64-0.23.2.klib
                |collection-iosx64-1.4.0.klib
                |foundation-layout-uikitx64-1.7.3.klib
                |foundation-uikitx64-1.7.3.klib
                |kotlinx-coroutines-core-iosx64-1.8.0.klib
                |kotlinx-datetime-iosx64-0.6.0.klib
                |kotlinx-serialization-core-iosx64-1.6.2.klib
                |lifecycle-common-iosx64-2.8.5.klib
                |lifecycle-runtime-compose-uikitx64-2.8.4.klib
                |lifecycle-runtime-iosx64-2.8.5.klib
                |lifecycle-viewmodel-iosx64-2.8.5.klib
                |material-icons-core-uikitx64-1.7.3.klib
                |material-ripple-uikitx64-1.7.3.klib
                |material3-uikitx64-1.7.3.klib
                |runtime-saveable-uikitx64-1.7.3.klib
                |runtime-uikitx64-1.7.3.klib
                |skiko-iosx64-0.8.18-cinterop-uikit.klib
                |skiko-iosx64-0.8.18.klib
                |ui-geometry-uikitx64-1.7.3.klib
                |ui-graphics-uikitx64-1.7.3.klib
                |ui-text-uikitx64-1.7.3.klib
                |ui-uikit-uikitx64-1.7.3-cinterop-utils.klib
                |ui-uikit-uikitx64-1.7.3.klib
                |ui-uikitx64-1.7.3.klib
                |ui-unit-uikitx64-1.7.3.klib
                |ui-util-uikitx64-1.7.3.klib
                """.trimMargin(),
            iosAppIosX64FragmentDeps
        )
    }

    @Test
    fun `test shared@iosX64Test dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)
        val iosAppIosX64FragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "shared",
                fragment = "iosX64Test",
                expected = """Fragment 'shared.iosX64Test' dependencies
+--- shared:iosX64Test:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|         \--- org.jetbrains.kotlin:kotlin-stdlib-common:${UsedVersions.kotlinVersion} (c)
+--- shared:iosX64Test:org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}
+--- shared:iosX64Test:org.jetbrains.kotlin:kotlin-test-annotations-common:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-test-annotations-common:${UsedVersions.kotlinVersion}
|         \--- org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}
+--- shared:iosX64Test:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3
|         \--- org.jetbrains.compose.runtime:runtime-uikitx64:1.7.3
|              +--- org.jetbrains.compose.collection-internal:collection:1.7.3
|              |    +--- androidx.collection:collection:1.4.0
|              |    |    \--- androidx.collection:collection-iosx64:1.4.0
|              |    |         +--- androidx.annotation:annotation:1.7.0 -> 1.8.0
|              |    |         |    \--- androidx.annotation:annotation-iosx64:1.8.0
|              |    |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> ${UsedVersions.kotlinVersion}
|              |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |    +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3
|              |    |    +--- androidx.annotation:annotation:1.8.0 (*)
|              |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |    \--- org.jetbrains.kotlinx:atomicfu:0.23.2
|              |         \--- org.jetbrains.kotlinx:atomicfu-iosx64:0.23.2
|              |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-iosx64:1.8.0
|                        +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
|                        \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
+--- shared:iosX64Test:org.jetbrains.compose.foundation:foundation:1.7.3
|    \--- org.jetbrains.compose.foundation:foundation:1.7.3
|         \--- org.jetbrains.compose.foundation:foundation-uikitx64:1.7.3
|              +--- org.jetbrains.compose.animation:animation:1.7.3
|              |    \--- org.jetbrains.compose.animation:animation-uikitx64:1.7.3
|              |         +--- org.jetbrains.compose.animation:animation-core:1.7.3
|              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitx64:1.7.3
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.7.3
|              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitx64:1.7.3
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-common:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-common-iosx64:2.8.5
|              |         |         |         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    |         +--- org.jetbrains.kotlinx:atomicfu:0.17.0 -> 0.23.2 (*)
|              |         |         |         |    |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-runtime:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-runtime-iosx64:2.8.5
|              |         |         |         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |    |         +--- androidx.lifecycle:lifecycle-common:2.8.5 (*)
|              |         |         |         |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|              |         |         |         |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4
|              |         |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitx64:2.8.4
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4 (*)
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.11 -> 1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-viewmodel:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-viewmodel-iosx64:2.8.5
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
|              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitx64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitx64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-uikitx64:1.7.3
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.7.3
|              |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-uikit-uikitx64:1.7.3
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitx64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitx64:1.7.3
|              |         |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.18
|              |         |         |         |              \--- org.jetbrains.skiko:skiko-iosx64:0.8.18
|              |         |         |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitx64:1.7.3
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
|              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitx64:1.7.3
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
+--- shared:iosX64Test:org.jetbrains.compose.material3:material3:1.7.3
|    \--- org.jetbrains.compose.material3:material3:1.7.3
|         \--- org.jetbrains.compose.material3:material3-uikitx64:1.7.3
|              +--- org.jetbrains.compose.animation:animation-core:1.7.3 (*)
|              +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
|              +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
|              +--- org.jetbrains.compose.material:material-icons-core:1.7.3
|              |    \--- org.jetbrains.compose.material:material-icons-core-uikitx64:1.7.3
|              |         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|              |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              +--- org.jetbrains.compose.material:material-ripple:1.7.3
|              |    \--- org.jetbrains.compose.material:material-ripple-uikitx64:1.7.3
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
|                   \--- org.jetbrains.kotlinx:kotlinx-datetime-iosx64:0.6.0
|                        +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
|                        |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-iosx64:1.6.2
|                        |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> ${UsedVersions.kotlinVersion} (*)
|                        |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|                        \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
\--- shared:iosX64Test:org.tinylog:tinylog-api-kotlin:2.6.2
     \--- org.tinylog:tinylog-api-kotlin:2.6.2
          +--- org.jetbrains.kotlin:kotlin-stdlib:1.4.32 -> ${UsedVersions.kotlinVersion}
          \--- org.tinylog:tinylog-api:2.6.2
                  """.trimIndent()
            )
        }

        assertFiles(
            files = """
                |animation-core-uikitx64-1.7.3.klib
                |animation-uikitx64-1.7.3.klib
                |annotation-iosx64-1.8.0.klib
                |atomicfu-iosx64-0.23.2-cinterop-interop.klib
                |atomicfu-iosx64-0.23.2.klib
                |collection-iosx64-1.4.0.klib
                |foundation-layout-uikitx64-1.7.3.klib
                |foundation-uikitx64-1.7.3.klib
                |kotlinx-coroutines-core-iosx64-1.8.0.klib
                |kotlinx-datetime-iosx64-0.6.0.klib
                |kotlinx-serialization-core-iosx64-1.6.2.klib
                |lifecycle-common-iosx64-2.8.5.klib
                |lifecycle-runtime-compose-uikitx64-2.8.4.klib
                |lifecycle-runtime-iosx64-2.8.5.klib
                |lifecycle-viewmodel-iosx64-2.8.5.klib
                |material-icons-core-uikitx64-1.7.3.klib
                |material-ripple-uikitx64-1.7.3.klib
                |material3-uikitx64-1.7.3.klib
                |runtime-saveable-uikitx64-1.7.3.klib
                |runtime-uikitx64-1.7.3.klib
                |skiko-iosx64-0.8.18-cinterop-uikit.klib
                |skiko-iosx64-0.8.18.klib
                |tinylog-api-2.6.2.jar
                |tinylog-api-kotlin-2.6.2.jar
                |ui-geometry-uikitx64-1.7.3.klib
                |ui-graphics-uikitx64-1.7.3.klib
                |ui-text-uikitx64-1.7.3.klib
                |ui-uikit-uikitx64-1.7.3-cinterop-utils.klib
                |ui-uikit-uikitx64-1.7.3.klib
                |ui-uikitx64-1.7.3.klib
                |ui-unit-uikitx64-1.7.3.klib
                |ui-util-uikitx64-1.7.3.klib
                """.trimMargin(),
            iosAppIosX64FragmentDeps
        )
    }

    /**
     * Since test fragment from one module can't reference test fragment of another module,
     * exported test dependency 'tinylog-api-kotlin' of shared module is not added to the fragment ios-app@iosX64Test.
     */
    @Test
    fun `test ios-app@iosX64Test dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)
        val iosAppIosX64FragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "ios-app",
                fragment = "iosX64Test",
                expected = """Fragment 'ios-app.iosX64Test' dependencies
+--- ios-app:iosX64Test:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|         \--- org.jetbrains.kotlin:kotlin-stdlib-common:${UsedVersions.kotlinVersion} (c)
+--- ios-app:iosX64Test:org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}
+--- ios-app:iosX64Test:org.jetbrains.kotlin:kotlin-test-annotations-common:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-test-annotations-common:${UsedVersions.kotlinVersion}
|         \--- org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}
+--- ios-app:iosX64Test:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3
|         \--- org.jetbrains.compose.runtime:runtime-uikitx64:1.7.3
|              +--- org.jetbrains.compose.collection-internal:collection:1.7.3
|              |    +--- androidx.collection:collection:1.4.0
|              |    |    \--- androidx.collection:collection-iosx64:1.4.0
|              |    |         +--- androidx.annotation:annotation:1.7.0 -> 1.8.0
|              |    |         |    \--- androidx.annotation:annotation-iosx64:1.8.0
|              |    |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> ${UsedVersions.kotlinVersion}
|              |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |    +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3
|              |    |    +--- androidx.annotation:annotation:1.8.0 (*)
|              |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |    \--- org.jetbrains.kotlinx:atomicfu:0.23.2
|              |         \--- org.jetbrains.kotlinx:atomicfu-iosx64:0.23.2
|              |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-iosx64:1.8.0
|                        +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
|                        \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
+--- ios-app:iosX64Test:org.jetbrains.compose.foundation:foundation:1.7.3
|    \--- org.jetbrains.compose.foundation:foundation:1.7.3
|         \--- org.jetbrains.compose.foundation:foundation-uikitx64:1.7.3
|              +--- org.jetbrains.compose.animation:animation:1.7.3
|              |    \--- org.jetbrains.compose.animation:animation-uikitx64:1.7.3
|              |         +--- org.jetbrains.compose.animation:animation-core:1.7.3
|              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitx64:1.7.3
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.7.3
|              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitx64:1.7.3
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-common:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-common-iosx64:2.8.5
|              |         |         |         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    |         +--- org.jetbrains.kotlinx:atomicfu:0.17.0 -> 0.23.2 (*)
|              |         |         |         |    |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-runtime:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-runtime-iosx64:2.8.5
|              |         |         |         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |    |         +--- androidx.lifecycle:lifecycle-common:2.8.5 (*)
|              |         |         |         |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|              |         |         |         |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4
|              |         |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitx64:2.8.4
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4 (*)
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.11 -> 1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-viewmodel:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-viewmodel-iosx64:2.8.5
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
|              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitx64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitx64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-uikitx64:1.7.3
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.7.3
|              |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-uikit-uikitx64:1.7.3
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitx64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitx64:1.7.3
|              |         |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.18
|              |         |         |         |              \--- org.jetbrains.skiko:skiko-iosx64:0.8.18
|              |         |         |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitx64:1.7.3
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
|              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitx64:1.7.3
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
\--- ios-app:iosX64Test:org.jetbrains.compose.material3:material3:1.7.3
     \--- org.jetbrains.compose.material3:material3:1.7.3
          \--- org.jetbrains.compose.material3:material3-uikitx64:1.7.3
               +--- org.jetbrains.compose.animation:animation-core:1.7.3 (*)
               +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
               +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
               +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
               +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
               +--- org.jetbrains.compose.material:material-icons-core:1.7.3
               |    \--- org.jetbrains.compose.material:material-icons-core-uikitx64:1.7.3
               |         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
               |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
               |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
               |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
               +--- org.jetbrains.compose.material:material-ripple:1.7.3
               |    \--- org.jetbrains.compose.material:material-ripple-uikitx64:1.7.3
               |         +--- org.jetbrains.compose.animation:animation:1.7.3 (*)
               |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
               |         +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
               |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
               |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
               |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
               +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
               +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
               +--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
               +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
               +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
               +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
               \--- org.jetbrains.kotlinx:kotlinx-datetime:0.6.0
                    \--- org.jetbrains.kotlinx:kotlinx-datetime-iosx64:0.6.0
                         +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
                         |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-iosx64:1.6.2
                         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> ${UsedVersions.kotlinVersion} (*)
                         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
                         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
                  """.trimIndent()
            )
        }

        assertFiles(
            files = """
                |animation-core-uikitx64-1.7.3.klib
                |animation-uikitx64-1.7.3.klib
                |annotation-iosx64-1.8.0.klib
                |atomicfu-iosx64-0.23.2-cinterop-interop.klib
                |atomicfu-iosx64-0.23.2.klib
                |collection-iosx64-1.4.0.klib
                |foundation-layout-uikitx64-1.7.3.klib
                |foundation-uikitx64-1.7.3.klib
                |kotlinx-coroutines-core-iosx64-1.8.0.klib
                |kotlinx-datetime-iosx64-0.6.0.klib
                |kotlinx-serialization-core-iosx64-1.6.2.klib
                |lifecycle-common-iosx64-2.8.5.klib
                |lifecycle-runtime-compose-uikitx64-2.8.4.klib
                |lifecycle-runtime-iosx64-2.8.5.klib
                |lifecycle-viewmodel-iosx64-2.8.5.klib
                |material-icons-core-uikitx64-1.7.3.klib
                |material-ripple-uikitx64-1.7.3.klib
                |material3-uikitx64-1.7.3.klib
                |runtime-saveable-uikitx64-1.7.3.klib
                |runtime-uikitx64-1.7.3.klib
                |skiko-iosx64-0.8.18-cinterop-uikit.klib
                |skiko-iosx64-0.8.18.klib
                |ui-geometry-uikitx64-1.7.3.klib
                |ui-graphics-uikitx64-1.7.3.klib
                |ui-text-uikitx64-1.7.3.klib
                |ui-uikit-uikitx64-1.7.3-cinterop-utils.klib
                |ui-uikit-uikitx64-1.7.3.klib
                |ui-uikitx64-1.7.3.klib
                |ui-unit-uikitx64-1.7.3.klib
                |ui-util-uikitx64-1.7.3.klib
                """.trimMargin(),
            iosAppIosX64FragmentDeps
        )
    }

    @Test
    fun `test ios-app@ios dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val iosAppIosFragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "ios-app",
                fragment = "ios",
                expected = """Fragment 'ios-app.ios' dependencies
+--- ios-app:ios:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
+--- ios-app:ios:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3
|         +--- org.jetbrains.kotlinx:atomicfu:0.23.2
|         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion}
|         |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|         |    +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
|         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|         \--- org.jetbrains.compose.collection-internal:collection:1.7.3
|              +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3
|              |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
+--- ios-app:ios:org.jetbrains.compose.foundation:foundation:1.7.3
|    \--- org.jetbrains.compose.foundation:foundation:1.7.3
|         +--- org.jetbrains.compose.animation:animation:1.7.3
|         |    +--- org.jetbrains.compose.animation:animation-core:1.7.3
|         |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|         |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    +--- org.jetbrains.compose.ui:ui:1.7.3
|         |    |    |    +--- org.jetbrains.compose.runtime:runtime-saveable:1.7.3
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.7.3
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    |    |    \--- org.jetbrains.compose.ui:ui-util:1.7.3
|         |    |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |         \--- org.jetbrains.compose.ui:ui-uikit:1.7.3
|         |    |    |    +--- org.jetbrains.compose.ui:ui-graphics:1.7.3
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.7.3
|         |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|         |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         |    |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.skiko:skiko:0.8.18
|         |    |    |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|         |    |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|         |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-text:1.7.3
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.skiko:skiko:0.8.18 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime-saveable:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|         |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.skiko:skiko:0.8.18 (*)
|         |    |    |    +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|         |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|         |    |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|         |    |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|         |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4
|         |    |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.11 -> 1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4
|         |    |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|         |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    \--- org.jetbrains.compose.ui:ui-uikit:1.7.3
|         |    |    +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|         |    |    +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         |    |    \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3
|         |    |    +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|         |    |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |    +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|         |    +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|         |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         |    +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|         |    \--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|         +--- org.jetbrains.skiko:skiko:0.8.18 (*)
|         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|         +--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
|         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|         +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
|         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|         \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
\--- ios-app:ios:org.jetbrains.compose.material3:material3:1.7.3
     \--- org.jetbrains.compose.material3:material3:1.7.3
          +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
          +--- org.jetbrains.compose.material:material-icons-core:1.7.3
          |    +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
          |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
          |    +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
          |    \--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
          +--- org.jetbrains.compose.material:material-ripple:1.7.3
          |    +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
          |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
          |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
          |    +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
          |    +--- org.jetbrains.compose.animation:animation:1.7.3 (*)
          |    \--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
          +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
          +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
          +--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
          +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
          +--- org.jetbrains.compose.animation:animation-core:1.7.3 (*)
          +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
          +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
          +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
          +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
          +--- org.jetbrains.kotlinx:kotlinx-datetime:0.6.0
          |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
          |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
          |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> ${UsedVersions.kotlinVersion} (*)
          |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
          \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
             """.trimIndent()
            )
        }
        assertFiles(
            files = """
                |animation-commonMain-1.7.3.klib
                |animation-core-commonMain-1.7.3.klib
                |animation-core-jbMain-1.7.3.klib
                |animation-core-jsNativeMain-1.7.3.klib
                |animation-core-uikitMain-1.7.3.klib
                |animation-jsNativeMain-1.7.3.klib
                |animation-nativeMain-1.7.3.klib
                |annotation-commonMain-1.7.3.klib
                |annotation-nonJvmMain-1.7.3.klib
                |atomicfu-commonMain-0.23.2.klib
                |atomicfu-nativeMain-0.23.2.klib
                |collection-commonMain-1.7.3.klib
                |collection-jbMain-1.7.3.klib
                |collection-jsNativeMain-1.7.3.klib
                |foundation-commonMain-1.7.3.klib
                |foundation-darwinMain-1.7.3.klib
                |foundation-jsNativeMain-1.7.3.klib
                |foundation-layout-commonMain-1.7.3.klib
                |foundation-layout-jsNativeMain-1.7.3.klib
                |foundation-layout-skikoMain-1.7.3.klib
                |foundation-layout-uikitMain-1.7.3.klib
                |foundation-nativeMain-1.7.3.klib
                |foundation-skikoMain-1.7.3.klib
                |foundation-uikitMain-1.7.3.klib
                |kotlin-stdlib-commonMain-${UsedVersions.kotlinVersion}.klib
                |kotlinx-coroutines-core-commonMain-1.8.0.klib
                |kotlinx-coroutines-core-concurrentMain-1.8.0.klib
                |kotlinx-coroutines-core-nativeDarwinMain-1.8.0.klib
                |kotlinx-coroutines-core-nativeMain-1.8.0.klib
                |kotlinx-datetime-commonMain-0.6.0.klib
                |kotlinx-datetime-darwinMain-0.6.0.klib
                |kotlinx-datetime-nativeMain-0.6.0.klib
                |kotlinx-datetime-tzdbOnFilesystemMain-0.6.0.klib
                |kotlinx-datetime-tzfileMain-0.6.0.klib
                |kotlinx-serialization-core-commonMain-1.6.2.klib
                |kotlinx-serialization-core-nativeMain-1.6.2.klib
                |lifecycle-common-commonMain-2.8.4.klib
                |lifecycle-common-nonJvmMain-2.8.4.klib
                |lifecycle-runtime-commonMain-2.8.4.klib
                |lifecycle-runtime-compose-commonMain-2.8.4.klib
                |lifecycle-runtime-compose-nonAndroidMain-2.8.4.klib
                |lifecycle-runtime-nativeMain-2.8.4.klib
                |lifecycle-runtime-nonJvmMain-2.8.4.klib
                |lifecycle-viewmodel-commonMain-2.8.4.klib
                |lifecycle-viewmodel-nativeMain-2.8.4.klib
                |lifecycle-viewmodel-nonJvmMain-2.8.4.klib
                |material-icons-core-commonMain-1.7.3.klib
                |material-ripple-commonMain-1.7.3.klib
                |material-ripple-jbMain-1.7.3.klib
                |material3-commonMain-1.7.3.klib
                |material3-darwinMain-1.7.3.klib
                |material3-jsNativeMain-1.7.3.klib
                |material3-nativeMain-1.7.3.klib
                |material3-nonJvmMain-1.7.3.klib
                |material3-skikoMain-1.7.3.klib
                |runtime-commonMain-1.7.3.klib
                |runtime-darwinMain-1.7.3.klib
                |runtime-jbMain-1.7.3.klib
                |runtime-jsNativeMain-1.7.3.klib
                |runtime-nativeMain-1.7.3.klib
                |runtime-posixMain-1.7.3.klib
                |runtime-saveable-commonMain-1.7.3.klib
                |runtime-uikitMain-1.7.3.klib
                |skiko-commonMain-0.8.18.klib
                |skiko-darwinMain-0.8.18.klib
                |skiko-iosMain-0.8.18.klib
                |skiko-nativeJsMain-0.8.18.klib
                |skiko-nativeMain-0.8.18.klib
                |skiko-uikitMain-0.8.18.klib
                |ui-commonMain-1.7.3.klib
                |ui-darwinMain-1.7.3.klib
                |ui-geometry-commonMain-1.7.3.klib
                |ui-graphics-commonMain-1.7.3.klib
                |ui-graphics-jsNativeMain-1.7.3.klib
                |ui-graphics-nativeMain-1.7.3.klib
                |ui-graphics-skikoExcludingWebMain-1.7.3.klib
                |ui-graphics-skikoMain-1.7.3.klib
                |ui-jsNativeMain-1.7.3.klib
                |ui-nativeMain-1.7.3.klib
                |ui-skikoMain-1.7.3.klib
                |ui-text-commonMain-1.7.3.klib
                |ui-text-darwinMain-1.7.3.klib
                |ui-text-jsNativeMain-1.7.3.klib
                |ui-text-nativeMain-1.7.3.klib
                |ui-text-skikoMain-1.7.3.klib
                |ui-uikit-uikitMain-1.7.3.klib
                |ui-uikitMain-1.7.3.klib
                |ui-unit-commonMain-1.7.3.klib
                |ui-unit-jbMain-1.7.3.klib
                |ui-unit-jsNativeMain-1.7.3.klib
                |ui-util-commonMain-1.7.3.klib
                |ui-util-nonJvmMain-1.7.3.klib
                |ui-util-uikitMain-1.7.3.klib
                """.trimMargin(),
            iosAppIosFragmentDeps
        )
    }

    @Test
    fun `test ios-app@iosX64 dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)
        val iosAppIosX64FragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "ios-app",
                fragment = "iosX64",
                expected = """Fragment 'ios-app.iosX64' dependencies
+--- ios-app:iosX64:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|         \--- org.jetbrains.kotlin:kotlin-stdlib-common:${UsedVersions.kotlinVersion} (c)
+--- ios-app:iosX64:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3
|         \--- org.jetbrains.compose.runtime:runtime-uikitx64:1.7.3
|              +--- org.jetbrains.compose.collection-internal:collection:1.7.3
|              |    +--- androidx.collection:collection:1.4.0
|              |    |    \--- androidx.collection:collection-iosx64:1.4.0
|              |    |         +--- androidx.annotation:annotation:1.7.0 -> 1.8.0
|              |    |         |    \--- androidx.annotation:annotation-iosx64:1.8.0
|              |    |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> ${UsedVersions.kotlinVersion}
|              |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |    +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3
|              |    |    +--- androidx.annotation:annotation:1.8.0 (*)
|              |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |    \--- org.jetbrains.kotlinx:atomicfu:0.23.2
|              |         \--- org.jetbrains.kotlinx:atomicfu-iosx64:0.23.2
|              |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-iosx64:1.8.0
|                        +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
|                        \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
+--- ios-app:iosX64:org.jetbrains.compose.foundation:foundation:1.7.3
|    \--- org.jetbrains.compose.foundation:foundation:1.7.3
|         \--- org.jetbrains.compose.foundation:foundation-uikitx64:1.7.3
|              +--- org.jetbrains.compose.animation:animation:1.7.3
|              |    \--- org.jetbrains.compose.animation:animation-uikitx64:1.7.3
|              |         +--- org.jetbrains.compose.animation:animation-core:1.7.3
|              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitx64:1.7.3
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.7.3
|              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitx64:1.7.3
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-common:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-common-iosx64:2.8.5
|              |         |         |         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    |         +--- org.jetbrains.kotlinx:atomicfu:0.17.0 -> 0.23.2 (*)
|              |         |         |         |    |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-runtime:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-runtime-iosx64:2.8.5
|              |         |         |         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |    |         +--- androidx.lifecycle:lifecycle-common:2.8.5 (*)
|              |         |         |         |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|              |         |         |         |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4
|              |         |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitx64:2.8.4
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4 (*)
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4 (*)
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.11 -> 1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.11 -> 1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.24 -> ${UsedVersions.kotlinVersion}
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4
|              |         |         |         |    +--- androidx.lifecycle:lifecycle-viewmodel:2.8.5
|              |         |         |         |    |    \--- androidx.lifecycle:lifecycle-viewmodel-iosx64:2.8.5
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
|              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitx64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitx64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-uikitx64:1.7.3
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.7.3
|              |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-uikit-uikitx64:1.7.3
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitx64:1.7.3
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitx64:1.7.3
|              |         |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.18
|              |         |         |         |              \--- org.jetbrains.skiko:skiko-iosx64:0.8.18
|              |         |         |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
|              |         |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.7.3
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitx64:1.7.3
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
|              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitx64:1.7.3
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
\--- ios-app:iosX64:org.jetbrains.compose.material3:material3:1.7.3
     \--- org.jetbrains.compose.material3:material3:1.7.3
          \--- org.jetbrains.compose.material3:material3-uikitx64:1.7.3
               +--- org.jetbrains.compose.animation:animation-core:1.7.3 (*)
               +--- org.jetbrains.compose.annotation-internal:annotation:1.7.3 (*)
               +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
               +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
               +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3 (*)
               +--- org.jetbrains.compose.material:material-icons-core:1.7.3
               |    \--- org.jetbrains.compose.material:material-icons-core-uikitx64:1.7.3
               |         +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
               |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
               |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
               |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
               +--- org.jetbrains.compose.material:material-ripple:1.7.3
               |    \--- org.jetbrains.compose.material:material-ripple-uikitx64:1.7.3
               |         +--- org.jetbrains.compose.animation:animation:1.7.3 (*)
               |         +--- org.jetbrains.compose.collection-internal:collection:1.7.3 (*)
               |         +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
               |         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
               |         +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
               |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
               +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
               +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
               +--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
               +--- org.jetbrains.compose.ui:ui-util:1.7.3 (*)
               +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24 -> ${UsedVersions.kotlinVersion} (*)
               +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
               \--- org.jetbrains.kotlinx:kotlinx-datetime:0.6.0
                    \--- org.jetbrains.kotlinx:kotlinx-datetime-iosx64:0.6.0
                         +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
                         |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-iosx64:1.6.2
                         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> ${UsedVersions.kotlinVersion} (*)
                         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
                         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion}
             """.trimIndent()
            )
        }

        assertFiles(
            files = """
                |animation-core-uikitx64-1.7.3.klib
                |animation-uikitx64-1.7.3.klib
                |annotation-iosx64-1.8.0.klib
                |atomicfu-iosx64-0.23.2-cinterop-interop.klib
                |atomicfu-iosx64-0.23.2.klib
                |collection-iosx64-1.4.0.klib
                |foundation-layout-uikitx64-1.7.3.klib
                |foundation-uikitx64-1.7.3.klib
                |kotlinx-coroutines-core-iosx64-1.8.0.klib
                |kotlinx-datetime-iosx64-0.6.0.klib
                |kotlinx-serialization-core-iosx64-1.6.2.klib
                |lifecycle-common-iosx64-2.8.5.klib
                |lifecycle-runtime-compose-uikitx64-2.8.4.klib
                |lifecycle-runtime-iosx64-2.8.5.klib
                |lifecycle-viewmodel-iosx64-2.8.5.klib
                |material-icons-core-uikitx64-1.7.3.klib
                |material-ripple-uikitx64-1.7.3.klib
                |material3-uikitx64-1.7.3.klib
                |runtime-saveable-uikitx64-1.7.3.klib
                |runtime-uikitx64-1.7.3.klib
                |skiko-iosx64-0.8.18-cinterop-uikit.klib
                |skiko-iosx64-0.8.18.klib
                |ui-geometry-uikitx64-1.7.3.klib
                |ui-graphics-uikitx64-1.7.3.klib
                |ui-text-uikitx64-1.7.3.klib
                |ui-uikit-uikitx64-1.7.3-cinterop-utils.klib
                |ui-uikit-uikitx64-1.7.3.klib
                |ui-uikitx64-1.7.3.klib
                |ui-unit-uikitx64-1.7.3.klib
                |ui-util-uikitx64-1.7.3.klib
                """.trimMargin(),
            iosAppIosX64FragmentDeps
        )
    }

    // todo (AB) : 'anrdoid-app.android' differs from what Gradle produce (versions).
    // todo (AB) : It seems it is caused by resolving RUNTIME version of library instead of COMPILE one being resolved by IdeSync.
    @Test
    fun `test android-app@android dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val androidAppAndroidFragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom),
                    ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "android-app",
                fragment = "android",
                expected = """Fragment 'android-app.android' dependencies
+--- android-app:android:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|         \--- org.jetbrains:annotations:13.0 -> 23.0.0
+--- android-app:android:org.jetbrains.kotlin:kotlin-stdlib-jdk7:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:${UsedVersions.kotlinVersion}
|         \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
+--- android-app:android:org.jetbrains.kotlin:kotlin-stdlib-jdk8:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:${UsedVersions.kotlinVersion}
|         +--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
|         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:${UsedVersions.kotlinVersion} (*)
+--- android-app:android:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3
|         +--- androidx.compose.runtime:runtime:1.7.6
|         |    \--- androidx.compose.runtime:runtime-android:1.7.6
|         |         +--- androidx.annotation:annotation-experimental:1.4.1
|         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> ${UsedVersions.kotlinVersion} (*)
|         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 -> 1.8.0
|         |         |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|         |         |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0
|         |         |    |         +--- org.jetbrains:annotations:23.0.0
|         |         |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.0
|         |         |    |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0 (c)
|         |         |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion} (*)
|         |         |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.0
|         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion} (*)
|         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
+--- android-app:android:androidx.activity:activity-compose:1.7.2
|    \--- androidx.activity:activity-compose:1.7.2
|         +--- androidx.activity:activity-ktx:1.7.2
|         |    +--- androidx.activity:activity:1.7.2
|         |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0
|         |    |    |    \--- androidx.annotation:annotation-jvm:1.8.0
|         |    |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    +--- androidx.core:core:1.8.0
|         |    |    |    +--- androidx.annotation:annotation:1.2.0 -> 1.8.0 (*)
|         |    |    |    +--- androidx.annotation:annotation-experimental:1.1.0 -> 1.4.1 (*)
|         |    |    |    +--- androidx.lifecycle:lifecycle-runtime:2.3.1 -> 2.8.5
|         |    |    |    |    \--- androidx.lifecycle:lifecycle-runtime-android:2.8.5
|         |    |    |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|         |    |    |    |         +--- androidx.arch.core:core-common:2.2.0
|         |    |    |    |         |    \--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |    |    |    |         +--- androidx.lifecycle:lifecycle-common:2.8.5
|         |    |    |    |         |    \--- androidx.lifecycle:lifecycle-common-jvm:2.8.5
|         |    |    |    |         |         +--- androidx.annotation:annotation:1.8.0 (*)
|         |    |    |    |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|         |    |    |    |         |         +--- androidx.lifecycle:lifecycle-runtime-compose:2.8.5 (c)
|         |    |    |    |         |         +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5 (c)
|         |    |    |    |         |         \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.5 (c)
|         |    |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 -> 1.8.0 (*)
|         |    |    |    |         +--- androidx.lifecycle:lifecycle-runtime-compose:2.8.5 (c)
|         |    |    |    |         +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5 (c)
|         |    |    |    |         \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.5 (c)
|         |    |    |    \--- androidx.versionedparcelable:versionedparcelable:1.1.1
|         |    |    |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |    |    |         \--- androidx.collection:collection:1.0.0 -> 1.4.0
|         |    |    |              \--- androidx.collection:collection-jvm:1.4.0
|         |    |    |                   +--- androidx.annotation:annotation:1.7.0 -> 1.8.0 (*)
|         |    |    |                   \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    +--- androidx.lifecycle:lifecycle-runtime:2.6.1 -> 2.8.5 (*)
|         |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 -> 2.8.5
|         |    |    |    \--- androidx.lifecycle:lifecycle-viewmodel-android:2.8.5
|         |    |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|         |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 -> 1.8.0 (*)
|         |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|         |    |    |         +--- androidx.lifecycle:lifecycle-runtime-compose:2.8.5 (c)
|         |    |    |         +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5 (c)
|         |    |    |         \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.5 (c)
|         |    |    +--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.1 -> 2.8.5
|         |    |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.8.0 (*)
|         |    |    |    +--- androidx.core:core-ktx:1.2.0
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.3.41 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |    |    |    |    \--- androidx.core:core:1.2.0 -> 1.8.0 (*)
|         |    |    |    +--- androidx.lifecycle:lifecycle-livedata-core:2.8.5
|         |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.8.5 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    |    +--- androidx.lifecycle:lifecycle-runtime-compose:2.8.5 (c)
|         |    |    |    |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5 (c)
|         |    |    |    |    \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.5 (c)
|         |    |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.8.5 (*)
|         |    |    |    +--- androidx.savedstate:savedstate:1.2.1
|         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 -> 1.8.0 (*)
|         |    |    |    +--- androidx.lifecycle:lifecycle-runtime-compose:2.8.5 (c)
|         |    |    |    \--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5 (c)
|         |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
|         |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> ${UsedVersions.kotlinVersion} (*)
|         |    +--- androidx.core:core-ktx:1.1.0 -> 1.2.0 (*)
|         |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.6.1 -> 2.8.5
|         |    |    \--- androidx.lifecycle:lifecycle-runtime-ktx-android:2.8.5
|         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|         |    |         +--- androidx.lifecycle:lifecycle-runtime:2.8.5 (*)
|         |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion} (*)
|         |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 -> 1.8.0 (*)
|         |    |         +--- androidx.lifecycle:lifecycle-runtime-compose:2.8.5 (c)
|         |    |         +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5 (c)
|         |    |         \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.5 (c)
|         |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1 -> 2.8.5
|         |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.8.5 (*)
|         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion} (*)
|         |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 -> 1.8.0 (*)
|         |    |    +--- androidx.lifecycle:lifecycle-runtime-compose:2.8.5 (c)
|         |    |    \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.5 (c)
|         |    +--- androidx.savedstate:savedstate-ktx:1.2.1
|         |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
|         |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> ${UsedVersions.kotlinVersion} (*)
|         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> ${UsedVersions.kotlinVersion} (*)
|         +--- androidx.compose.runtime:runtime:1.0.1 -> 1.7.6 (*)
|         +--- androidx.compose.runtime:runtime-saveable:1.0.1 -> 1.7.6
|         |    \--- androidx.compose.runtime:runtime-saveable-android:1.7.6
|         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         \--- androidx.compose.runtime:runtime:1.7.6 (*)
|         +--- androidx.compose.ui:ui:1.0.1 -> 1.7.6
|         |    \--- androidx.compose.ui:ui-android:1.7.6
|         |         +--- androidx.annotation:annotation:1.8.0 (*)
|         |         +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         +--- androidx.compose.runtime:runtime-saveable:1.7.6 (*)
|         |         +--- androidx.compose.ui:ui-geometry:1.7.6
|         |         |    \--- androidx.compose.ui:ui-geometry-android:1.7.6
|         |         |         \--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         +--- androidx.compose.ui:ui-graphics:1.7.6
|         |         |    \--- androidx.compose.ui:ui-graphics-android:1.7.6
|         |         |         +--- androidx.annotation:annotation:1.7.0 -> 1.8.0 (*)
|         |         |         +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         |         \--- androidx.compose.ui:ui-unit:1.7.6
|         |         |              \--- androidx.compose.ui:ui-unit-android:1.7.6
|         |         |                   +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |                   +--- androidx.annotation:annotation:1.8.0 (*)
|         |         |                   +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         |                   \--- androidx.compose.ui:ui-geometry:1.7.6 (*)
|         |         +--- androidx.compose.ui:ui-text:1.7.6
|         |         |    \--- androidx.compose.ui:ui-text-android:1.7.6
|         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         |         +--- androidx.compose.ui:ui-graphics:1.7.6 (*)
|         |         |         \--- androidx.compose.ui:ui-unit:1.7.6 (*)
|         |         +--- androidx.compose.ui:ui-unit:1.7.6 (*)
|         |         +--- androidx.compose.ui:ui-util:1.7.6
|         |         |    \--- androidx.compose.ui:ui-util-android:1.7.6
|         |         |         \--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         \--- androidx.lifecycle:lifecycle-runtime-compose:2.8.3 -> 2.8.5
|         |              \--- androidx.lifecycle:lifecycle-runtime-compose-android:2.8.5
|         |                   +--- androidx.annotation:annotation:1.8.0 (*)
|         |                   +--- androidx.compose.runtime:runtime:1.6.5 -> 1.7.6 (*)
|         |                   +--- androidx.lifecycle:lifecycle-runtime:2.8.5 (*)
|         |                   +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.5 (*)
|         |                   +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5 (c)
|         |                   \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.5 (c)
|         \--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 -> 2.8.5 (*)
+--- android-app:android:org.jetbrains.compose.foundation:foundation:1.7.3
|    \--- org.jetbrains.compose.foundation:foundation:1.7.3
|         +--- androidx.compose.foundation:foundation:1.7.6
|         |    \--- androidx.compose.foundation:foundation-android:1.7.6
|         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         +--- androidx.collection:collection:1.4.0 (*)
|         |         +--- androidx.compose.animation:animation:1.7.6
|         |         |    \--- androidx.compose.animation:animation-android:1.7.6
|         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         |         +--- androidx.compose.animation:animation-core:1.7.6
|         |         |         |    \--- androidx.compose.animation:animation-core-android:1.7.6
|         |         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|         |         |         +--- androidx.compose.foundation:foundation-layout:1.7.6
|         |         |         |    \--- androidx.compose.foundation:foundation-layout-android:1.7.6
|         |         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         |         +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         |         |         \--- androidx.compose.ui:ui:1.7.6 (*)
|         |         |         +--- androidx.compose.runtime:runtime:1.7.6 (*)
|         |         |         \--- androidx.compose.ui:ui-geometry:1.7.6 (*)
|         |         +--- androidx.compose.runtime:runtime:1.7.6 (*)
|         |         \--- androidx.compose.ui:ui:1.7.6 (*)
|         +--- org.jetbrains.compose.animation:animation:1.7.3
|         |    +--- androidx.compose.animation:animation:1.7.6 (*)
|         |    +--- org.jetbrains.compose.animation:animation-core:1.7.3
|         |    |    +--- androidx.compose.animation:animation-core:1.7.6 (*)
|         |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3
|         |    |    +--- androidx.compose.foundation:foundation-layout:1.7.6 (*)
|         |    |    \--- org.jetbrains.compose.ui:ui:1.7.3
|         |    |         +--- androidx.compose.ui:ui:1.7.6 (*)
|         |    |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.7.3
|         |    |         |    +--- androidx.compose.runtime:runtime-saveable:1.7.6 (*)
|         |    |         |    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3
|         |    |         |    \--- androidx.compose.ui:ui-geometry:1.7.6 (*)
|         |    |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3
|         |    |         |    +--- androidx.compose.ui:ui-graphics:1.7.6 (*)
|         |    |         |    \--- org.jetbrains.compose.ui:ui-unit:1.7.3
|         |    |         |         +--- androidx.compose.ui:ui-unit:1.7.6 (*)
|         |    |         |         \--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|         |    |         +--- org.jetbrains.compose.ui:ui-text:1.7.3
|         |    |         |    +--- androidx.compose.ui:ui-text:1.7.6 (*)
|         |    |         |    +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|         |    |         |    \--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|         |    |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|         |    |         \--- org.jetbrains.compose.ui:ui-util:1.7.3
|         |    |              \--- androidx.compose.ui:ui-util:1.7.6 (*)
|         |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|         |    \--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         \--- org.jetbrains.compose.ui:ui:1.7.3 (*)
\--- android-app:android:org.jetbrains.compose.material3:material3:1.7.3
     \--- org.jetbrains.compose.material3:material3:1.7.3
          +--- androidx.compose.material3:material3:1.3.1
          |    \--- androidx.compose.material3:material3-android:1.3.1
          |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
          |         +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
          |         +--- androidx.compose.foundation:foundation:1.7.0 -> 1.7.6 (*)
          |         +--- androidx.compose.foundation:foundation-layout:1.7.0 -> 1.7.6 (*)
          |         +--- androidx.compose.material:material-icons-core:1.6.0 -> 1.7.6
          |         |    \--- androidx.compose.material:material-icons-core-android:1.7.6
          |         |         \--- androidx.compose.ui:ui:1.6.0 -> 1.7.6 (*)
          |         +--- androidx.compose.material:material-ripple:1.7.0 -> 1.7.6
          |         |    \--- androidx.compose.material:material-ripple-android:1.7.6
          |         |         +--- androidx.compose.foundation:foundation:1.7.6 (*)
          |         |         \--- androidx.compose.runtime:runtime:1.7.6 (*)
          |         +--- androidx.compose.runtime:runtime:1.7.0 -> 1.7.6 (*)
          |         +--- androidx.compose.ui:ui:1.6.0 -> 1.7.6 (*)
          |         \--- androidx.compose.ui:ui-text:1.6.0 -> 1.7.6 (*)
          +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
          +--- org.jetbrains.compose.material:material-icons-core:1.7.3
          |    +--- androidx.compose.material:material-icons-core:1.7.6 (*)
          |    \--- org.jetbrains.compose.ui:ui:1.7.3 (*)
          +--- org.jetbrains.compose.material:material-ripple:1.7.3
          |    +--- androidx.compose.material:material-ripple:1.7.6 (*)
          |    +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
          |    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
          +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
          +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
          \--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
             """.trimIndent()
            )
        }
        // todo (AB) : Some versions are incorrect (?) - check difference with Gradle
        assertFiles(
            files = """
                |activity-1.7.2.aar
                |activity-compose-1.7.2.aar
                |activity-ktx-1.7.2.aar
                |animation-android-1.7.6.aar
                |animation-core-android-1.7.6.aar
                |annotation-experimental-1.4.1.aar
                |annotation-jvm-1.8.0.jar
                |annotations-23.0.0.jar
                |collection-jvm-1.4.0.jar
                |core-1.8.0.aar
                |core-common-2.2.0.jar
                |core-ktx-1.2.0.aar
                |foundation-android-1.7.6.aar
                |foundation-layout-android-1.7.6.aar
                |kotlin-stdlib-${UsedVersions.kotlinVersion}.jar
                |kotlin-stdlib-jdk7-${UsedVersions.kotlinVersion}.jar
                |kotlin-stdlib-jdk8-${UsedVersions.kotlinVersion}.jar
                |kotlinx-coroutines-android-1.8.0.jar
                |kotlinx-coroutines-core-jvm-1.8.0.jar
                |lifecycle-common-jvm-2.8.5.jar
                |lifecycle-livedata-core-2.8.5.aar
                |lifecycle-runtime-android-2.8.5.aar
                |lifecycle-runtime-compose-android-2.8.5.aar
                |lifecycle-runtime-ktx-android-2.8.5.aar
                |lifecycle-viewmodel-android-2.8.5.aar
                |lifecycle-viewmodel-ktx-2.8.5.aar
                |lifecycle-viewmodel-savedstate-2.8.5.aar
                |material-icons-core-android-1.7.6.aar
                |material-ripple-android-1.7.6.aar
                |material3-android-1.3.1.aar
                |runtime-android-1.7.6.aar
                |runtime-saveable-android-1.7.6.aar
                |savedstate-1.2.1.aar
                |savedstate-ktx-1.2.1.aar
                |ui-android-1.7.6.aar
                |ui-geometry-android-1.7.6.aar
                |ui-graphics-android-1.7.6.aar
                |ui-text-android-1.7.6.aar
                |ui-unit-android-1.7.6.aar
                |ui-util-android-1.7.6.aar
                |versionedparcelable-1.1.1.aar
                """.trimMargin(),
            androidAppAndroidFragmentDeps
        )
    }

    @Test
    fun `test shared@android dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val sharedAndroidFragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "shared",
                fragment = "android",
                expected = """Fragment 'shared.android' dependencies
+--- shared:android:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
|         \--- org.jetbrains:annotations:13.0 -> 23.0.0
+--- shared:android:org.jetbrains.kotlin:kotlin-stdlib-jdk7:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:${UsedVersions.kotlinVersion}
|         \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
+--- shared:android:org.jetbrains.kotlin:kotlin-stdlib-jdk8:${UsedVersions.kotlinVersion}, implicit
|    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:${UsedVersions.kotlinVersion}
|         +--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
|         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:${UsedVersions.kotlinVersion} (*)
+--- shared:android:org.jetbrains.compose.runtime:runtime:1.7.3, implicit
|    \--- org.jetbrains.compose.runtime:runtime:1.7.3
|         +--- androidx.compose.runtime:runtime:1.7.6
|         |    \--- androidx.compose.runtime:runtime-android:1.7.6
|         |         +--- androidx.annotation:annotation-experimental:1.4.1
|         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> ${UsedVersions.kotlinVersion} (*)
|         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 -> 1.8.0
|         |         |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|         |         |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0
|         |         |    |         +--- org.jetbrains:annotations:23.0.0
|         |         |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.0
|         |         |    |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0 (c)
|         |         |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion} (*)
|         |         |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.0
|         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> ${UsedVersions.kotlinVersion} (*)
|         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
+--- shared:android:org.jetbrains.compose.foundation:foundation:1.7.3
|    \--- org.jetbrains.compose.foundation:foundation:1.7.3
|         +--- androidx.compose.foundation:foundation:1.7.6
|         |    \--- androidx.compose.foundation:foundation-android:1.7.6
|         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0
|         |         |    \--- androidx.annotation:annotation-jvm:1.8.0
|         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> ${UsedVersions.kotlinVersion} (*)
|         |         +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         +--- androidx.collection:collection:1.4.0
|         |         |    \--- androidx.collection:collection-jvm:1.4.0
|         |         |         +--- androidx.annotation:annotation:1.7.0 -> 1.8.0 (*)
|         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion} (*)
|         |         +--- androidx.compose.animation:animation:1.7.6
|         |         |    \--- androidx.compose.animation:animation-android:1.7.6
|         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         |         +--- androidx.compose.animation:animation-core:1.7.6
|         |         |         |    \--- androidx.compose.animation:animation-core-android:1.7.6
|         |         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|         |         |         +--- androidx.compose.foundation:foundation-layout:1.7.6
|         |         |         |    \--- androidx.compose.foundation:foundation-layout-android:1.7.6
|         |         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         |         +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         |         |         \--- androidx.compose.ui:ui:1.7.6
|         |         |         |              \--- androidx.compose.ui:ui-android:1.7.6
|         |         |         |                   +--- androidx.annotation:annotation:1.8.0 (*)
|         |         |         |                   +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         |         |                   +--- androidx.compose.runtime:runtime-saveable:1.7.6
|         |         |         |                   |    \--- androidx.compose.runtime:runtime-saveable-android:1.7.6
|         |         |         |                   |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         |                   |         \--- androidx.compose.runtime:runtime:1.7.6 (*)
|         |         |         |                   +--- androidx.compose.ui:ui-geometry:1.7.6
|         |         |         |                   |    \--- androidx.compose.ui:ui-geometry-android:1.7.6
|         |         |         |                   |         \--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         |                   +--- androidx.compose.ui:ui-graphics:1.7.6
|         |         |         |                   |    \--- androidx.compose.ui:ui-graphics-android:1.7.6
|         |         |         |                   |         +--- androidx.annotation:annotation:1.7.0 -> 1.8.0 (*)
|         |         |         |                   |         +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         |         |                   |         \--- androidx.compose.ui:ui-unit:1.7.6
|         |         |         |                   |              \--- androidx.compose.ui:ui-unit-android:1.7.6
|         |         |         |                   |                   +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         |                   |                   +--- androidx.annotation:annotation:1.8.0 (*)
|         |         |         |                   |                   +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         |         |                   |                   \--- androidx.compose.ui:ui-geometry:1.7.6 (*)
|         |         |         |                   +--- androidx.compose.ui:ui-text:1.7.6
|         |         |         |                   |    \--- androidx.compose.ui:ui-text-android:1.7.6
|         |         |         |                   |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         |                   |         +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         |         |                   |         +--- androidx.compose.ui:ui-graphics:1.7.6 (*)
|         |         |         |                   |         \--- androidx.compose.ui:ui-unit:1.7.6 (*)
|         |         |         |                   +--- androidx.compose.ui:ui-unit:1.7.6 (*)
|         |         |         |                   +--- androidx.compose.ui:ui-util:1.7.6
|         |         |         |                   |    \--- androidx.compose.ui:ui-util-android:1.7.6
|         |         |         |                   |         \--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
|         |         |         |                   \--- androidx.lifecycle:lifecycle-runtime-compose:2.8.3 -> 2.8.5
|         |         |         |                        \--- androidx.lifecycle:lifecycle-runtime-compose-android:2.8.5
|         |         |         |                             +--- androidx.annotation:annotation:1.8.0 (*)
|         |         |         |                             +--- androidx.compose.runtime:runtime:1.6.5 -> 1.7.6 (*)
|         |         |         |                             +--- androidx.lifecycle:lifecycle-runtime:2.8.5
|         |         |         |                             |    \--- androidx.lifecycle:lifecycle-runtime-android:2.8.5
|         |         |         |                             |         +--- androidx.annotation:annotation:1.8.0 (*)
|         |         |         |                             |         +--- androidx.arch.core:core-common:2.2.0
|         |         |         |                             |         |    \--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         |                             |         +--- androidx.lifecycle:lifecycle-common:2.8.5
|         |         |         |                             |         |    \--- androidx.lifecycle:lifecycle-common-jvm:2.8.5
|         |         |         |                             |         |         +--- androidx.annotation:annotation:1.8.0 (*)
|         |         |         |                             |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion} (*)
|         |         |         |                             |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|         |         |         |                             |         |         \--- androidx.lifecycle:lifecycle-runtime-compose:2.8.5 (c)
|         |         |         |                             |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion} (*)
|         |         |         |                             |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 -> 1.8.0 (*)
|         |         |         |                             |         \--- androidx.lifecycle:lifecycle-runtime-compose:2.8.5 (c)
|         |         |         |                             \--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.5
|         |         |         |                                  \--- androidx.lifecycle:lifecycle-runtime-ktx-android:2.8.5
|         |         |         |                                       +--- androidx.annotation:annotation:1.8.0 (*)
|         |         |         |                                       +--- androidx.lifecycle:lifecycle-runtime:2.8.5 (*)
|         |         |         |                                       +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> ${UsedVersions.kotlinVersion} (*)
|         |         |         |                                       +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 -> 1.8.0 (*)
|         |         |         |                                       \--- androidx.lifecycle:lifecycle-runtime-compose:2.8.5 (c)
|         |         |         +--- androidx.compose.runtime:runtime:1.7.6 (*)
|         |         |         \--- androidx.compose.ui:ui-geometry:1.7.6 (*)
|         |         +--- androidx.compose.runtime:runtime:1.7.6 (*)
|         |         \--- androidx.compose.ui:ui:1.7.6 (*)
|         +--- org.jetbrains.compose.animation:animation:1.7.3
|         |    +--- androidx.compose.animation:animation:1.7.6 (*)
|         |    +--- org.jetbrains.compose.animation:animation-core:1.7.3
|         |    |    +--- androidx.compose.animation:animation-core:1.7.6 (*)
|         |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    +--- org.jetbrains.compose.foundation:foundation-layout:1.7.3
|         |    |    +--- androidx.compose.foundation:foundation-layout:1.7.6 (*)
|         |    |    \--- org.jetbrains.compose.ui:ui:1.7.3
|         |    |         +--- androidx.compose.ui:ui:1.7.6 (*)
|         |    |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.7.3
|         |    |         |    +--- androidx.compose.runtime:runtime-saveable:1.7.6 (*)
|         |    |         |    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    |         +--- org.jetbrains.compose.ui:ui-geometry:1.7.3
|         |    |         |    \--- androidx.compose.ui:ui-geometry:1.7.6 (*)
|         |    |         +--- org.jetbrains.compose.ui:ui-graphics:1.7.3
|         |    |         |    +--- androidx.compose.ui:ui-graphics:1.7.6 (*)
|         |    |         |    \--- org.jetbrains.compose.ui:ui-unit:1.7.3
|         |    |         |         +--- androidx.compose.ui:ui-unit:1.7.6 (*)
|         |    |         |         \--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|         |    |         +--- org.jetbrains.compose.ui:ui-text:1.7.3
|         |    |         |    +--- androidx.compose.ui:ui-text:1.7.6 (*)
|         |    |         |    +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
|         |    |         |    \--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|         |    |         +--- org.jetbrains.compose.ui:ui-unit:1.7.3 (*)
|         |    |         \--- org.jetbrains.compose.ui:ui-util:1.7.3
|         |    |              \--- androidx.compose.ui:ui-util:1.7.6 (*)
|         |    +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         |    +--- org.jetbrains.compose.ui:ui:1.7.3 (*)
|         |    \--- org.jetbrains.compose.ui:ui-geometry:1.7.3 (*)
|         +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
|         \--- org.jetbrains.compose.ui:ui:1.7.3 (*)
\--- shared:android:org.jetbrains.compose.material3:material3:1.7.3
     \--- org.jetbrains.compose.material3:material3:1.7.3
          +--- androidx.compose.material3:material3:1.3.1
          |    \--- androidx.compose.material3:material3-android:1.3.1
          |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
          |         +--- androidx.annotation:annotation-experimental:1.4.0 -> 1.4.1 (*)
          |         +--- androidx.compose.foundation:foundation:1.7.0 -> 1.7.6 (*)
          |         +--- androidx.compose.foundation:foundation-layout:1.7.0 -> 1.7.6 (*)
          |         +--- androidx.compose.material:material-icons-core:1.6.0 -> 1.7.6
          |         |    \--- androidx.compose.material:material-icons-core-android:1.7.6
          |         |         \--- androidx.compose.ui:ui:1.6.0 -> 1.7.6 (*)
          |         +--- androidx.compose.material:material-ripple:1.7.0 -> 1.7.6
          |         |    \--- androidx.compose.material:material-ripple-android:1.7.6
          |         |         +--- androidx.compose.foundation:foundation:1.7.6 (*)
          |         |         \--- androidx.compose.runtime:runtime:1.7.6 (*)
          |         +--- androidx.compose.runtime:runtime:1.7.0 -> 1.7.6 (*)
          |         +--- androidx.compose.ui:ui:1.6.0 -> 1.7.6 (*)
          |         \--- androidx.compose.ui:ui-text:1.6.0 -> 1.7.6 (*)
          +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
          +--- org.jetbrains.compose.material:material-icons-core:1.7.3
          |    +--- androidx.compose.material:material-icons-core:1.7.6 (*)
          |    \--- org.jetbrains.compose.ui:ui:1.7.3 (*)
          +--- org.jetbrains.compose.material:material-ripple:1.7.3
          |    +--- androidx.compose.material:material-ripple:1.7.6 (*)
          |    +--- org.jetbrains.compose.foundation:foundation:1.7.3 (*)
          |    \--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
          +--- org.jetbrains.compose.runtime:runtime:1.7.3 (*)
          +--- org.jetbrains.compose.ui:ui-graphics:1.7.3 (*)
          \--- org.jetbrains.compose.ui:ui-text:1.7.3 (*)
             """.trimIndent()
            )
        }
        // todo (AB) : Some versions are incorrect (?) - check difference with Gradle
        assertFiles(
            files = """
                |animation-android-1.7.6.aar
                |animation-core-android-1.7.6.aar
                |annotation-experimental-1.4.1.aar
                |annotation-jvm-1.8.0.jar
                |annotations-23.0.0.jar
                |collection-jvm-1.4.0.jar
                |core-common-2.2.0.jar
                |foundation-android-1.7.6.aar
                |foundation-layout-android-1.7.6.aar
                |kotlin-stdlib-${UsedVersions.kotlinVersion}.jar
                |kotlin-stdlib-jdk7-${UsedVersions.kotlinVersion}.jar
                |kotlin-stdlib-jdk8-${UsedVersions.kotlinVersion}.jar
                |kotlinx-coroutines-android-1.8.0.jar
                |kotlinx-coroutines-core-jvm-1.8.0.jar
                |lifecycle-common-jvm-2.8.5.jar
                |lifecycle-runtime-android-2.8.5.aar
                |lifecycle-runtime-compose-android-2.8.5.aar
                |lifecycle-runtime-ktx-android-2.8.5.aar
                |material-icons-core-android-1.7.6.aar
                |material-ripple-android-1.7.6.aar
                |material3-android-1.3.1.aar
                |runtime-android-1.7.6.aar
                |runtime-saveable-android-1.7.6.aar
                |ui-android-1.7.6.aar
                |ui-geometry-android-1.7.6.aar
                |ui-graphics-android-1.7.6.aar
                |ui-text-android-1.7.6.aar
                |ui-unit-android-1.7.6.aar
                |ui-util-android-1.7.6.aar
                """.trimMargin(),
            sharedAndroidFragmentDeps
        )
    }

    /**
     * Publishing of a KMP library involves publishing various variants of this library for different platforms.
     * DR provides API [MavenDependencyNode.getMavenCoordinatesForPublishing]
     * for getting coordinates of the platform-specific variants of KMP libraries.
     * Those coordinates are used for the module publishing instead of references on KMP libraries.
     *
     * This test verifies that DR API provides correct coordinates of the platform-specific
     * variants for KMP libraries.
     */
    @Test
    fun `test publication of shared KMP module for single platform`() {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val sharedModuleDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.ClassPathType(
                        ResolutionScope.COMPILE,
                        setOf(ResolutionPlatform.JVM),
                        isTest = false,
                        includeNonExportedNative = false
                    ),
                    ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "shared"
            ) as ModuleDependencyNodeWithModule
        }

        sharedModuleDeps.assertMapping(
            mapOf(
                "org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}" to "org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}",
                "org.jetbrains.compose.runtime:runtime:1.7.3" to "org.jetbrains.compose.runtime:runtime-desktop:1.7.3",
                "org.jetbrains.compose.foundation:foundation:1.7.3" to "org.jetbrains.compose.foundation:foundation-desktop:1.7.3",
                "org.jetbrains.compose.material3:material3:1.7.3" to "org.jetbrains.compose.material3:material3-desktop:1.7.3",
            )
        )
    }

    private fun ModuleDependencyNodeWithModule.assertMapping(
        expectedMapping: Map<String, String>
    ) {
        val expectedCoordinatesMapping =
            expectedMapping.map { it.key.toMavenCoordinates() to it.value.toMavenCoordinates() }.toMap()
        this
            .children
            .filterIsInstance<DirectFragmentDependencyNodeHolder>()
            .filter { it.dependencyNode is MavenDependencyNode }
            .forEach { directMavenDependency ->
                val node = directMavenDependency.dependencyNode as MavenDependencyNode

                val originalCoordinates = node.getOriginalMavenCoordinates()
                val expectedCoordinatesForPublishing = expectedCoordinatesMapping[originalCoordinates]
                val actualCoordinatesForPublishing = node.getMavenCoordinatesForPublishing()

                assertNotNull(
                    expectedCoordinatesForPublishing,
                    "Library with coordinates [$originalCoordinates] is absent among direct module dependencies."
                ) {}
                assertEquals(
                    expectedCoordinatesForPublishing, actualCoordinatesForPublishing,
                    "Unexpected coordinates for publishing were resolved for the library [$originalCoordinates]"
                )
            }
    }

    private fun String.toMavenCoordinates() =
        split(":").let { MavenCoordinates(it[0], it[1], it[2]) }
}
