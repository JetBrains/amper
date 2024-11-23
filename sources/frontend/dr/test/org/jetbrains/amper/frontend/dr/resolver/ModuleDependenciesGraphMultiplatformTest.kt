/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.Message
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.aomBuilder.SchemaBasedModelImport
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

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
class ModuleDependenciesGraphMultiplatformTest {

    private val testDataRoot: Path = TestUtil.amperSourcesRoot.resolve("frontend/dr/testData/projects")

    @Test
    fun `test shared@ios dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform")

        val sharedIosFragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL),
                module = "shared",
                fragment = "ios",
                expected = """Fragment 'shared.ios' dependencies
+--- dep:shared:ios:org.jetbrains.compose.foundation:foundation:1.6.10
|    \--- org.jetbrains.compose.foundation:foundation:1.6.10
|         +--- org.jetbrains.compose.animation:animation:1.6.10
|         |    +--- org.jetbrains.compose.animation:animation-core:1.6.10
|         |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|         |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|         |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|         |    |    +--- org.jetbrains.compose.collection-internal:collection:1.6.10
|         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|         |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|         |    |    |    \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10
|         |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|         |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    \--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|         |    |    +--- org.jetbrains.compose.ui:ui:1.6.10
|         |    |    |    +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.6.10
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    |    \--- org.jetbrains.compose.ui:ui-util:1.6.10
|         |    |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    |         \--- org.jetbrains.compose.ui:ui-uikit:1.6.10
|         |    |    |    +--- org.jetbrains.compose.ui:ui-graphics:1.6.10
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.6.10
|         |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|         |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         |    |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.skiko:skiko:0.8.4
|         |    |    |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|         |    |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|         |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-text:1.6.10
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.skiko:skiko:0.8.4 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|         |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.skiko:skiko:0.8.4 (*)
|         |    |    |    +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|         |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|         |    |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0 (*)
|         |    |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.0
|         |    |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0 (*)
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.0
|         |    |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|         |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    \--- org.jetbrains.compose.ui:ui-uikit:1.6.10
|         |    |    +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|         |    |    +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10
|         |    |    +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|         |    +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|         |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    \--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|         +--- org.jetbrains.skiko:skiko:0.8.4 (*)
|         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|         +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
|         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
|         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
+--- dep:shared:ios:org.jetbrains.compose.material3:material3:1.6.10
|    \--- org.jetbrains.compose.material3:material3:1.6.10
|         +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
|         +--- org.jetbrains.compose.material:material-icons-core:1.6.10
|         |    +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|         |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
|         |    \--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|         +--- org.jetbrains.compose.material:material-ripple:1.6.10
|         |    +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
|         |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    +--- org.jetbrains.compose.animation:animation:1.6.10 (*)
|         |    \--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
|         +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
|         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         +--- org.jetbrains.compose.animation:animation-core:1.6.10 (*)
|         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
|         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|         +--- org.jetbrains.kotlinx:kotlinx-datetime:0.5.0
|         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|         |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
|         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> 2.0.21 (*)
|         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|         \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
+--- dep:shared:ios:org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
\--- dep:shared:ios:org.jetbrains.compose.runtime:runtime:1.6.10
     \--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
             """.trimIndent()
            )
        }
            assertFiles(
                files = """
                |animation-commonMain-1.6.10.klib
                |animation-core-commonMain-1.6.10.klib
                |animation-core-jbMain-1.6.10.klib
                |animation-core-jsNativeMain-1.6.10.klib
                |animation-core-uikitMain-1.6.10.klib
                |animation-jsNativeMain-1.6.10.klib
                |animation-nativeMain-1.6.10.klib
                |annotation-commonMain-1.6.10.klib
                |annotation-nonJvmMain-1.6.10.klib
                |atomicfu-commonMain-0.23.2.klib
                |atomicfu-nativeMain-0.23.2.klib
                |collection-commonMain-1.6.10.klib
                |collection-jbMain-1.6.10.klib
                |collection-jsNativeMain-1.6.10.klib
                |foundation-commonMain-1.6.10.klib
                |foundation-darwinMain-1.6.10.klib
                |foundation-jsNativeMain-1.6.10.klib
                |foundation-layout-commonMain-1.6.10.klib
                |foundation-layout-jsNativeMain-1.6.10.klib
                |foundation-layout-skikoMain-1.6.10.klib
                |foundation-layout-uikitMain-1.6.10.klib
                |foundation-nativeMain-1.6.10.klib
                |foundation-skikoMain-1.6.10.klib
                |foundation-uikitMain-1.6.10.klib
                |kotlin-stdlib-commonMain-2.0.21.klib
                |kotlinx-coroutines-core-commonMain-1.8.0.klib
                |kotlinx-coroutines-core-concurrentMain-1.8.0.klib
                |kotlinx-coroutines-core-nativeDarwinMain-1.8.0.klib
                |kotlinx-coroutines-core-nativeMain-1.8.0.klib
                |kotlinx-datetime-commonMain-0.5.0.klib
                |kotlinx-datetime-darwinMain-0.5.0.klib
                |kotlinx-datetime-nativeMain-0.5.0.klib
                |kotlinx-serialization-core-commonMain-1.6.2.klib
                |kotlinx-serialization-core-nativeMain-1.6.2.klib
                |lifecycle-common-commonMain-2.8.0.klib
                |lifecycle-common-nonJvmMain-2.8.0.klib
                |lifecycle-runtime-commonMain-2.8.0.klib
                |lifecycle-runtime-compose-commonMain-2.8.0.klib
                |lifecycle-runtime-nativeMain-2.8.0.klib
                |lifecycle-runtime-nonJvmMain-2.8.0.klib
                |lifecycle-viewmodel-commonMain-2.8.0.klib
                |lifecycle-viewmodel-nativeMain-2.8.0.klib
                |lifecycle-viewmodel-nonJvmMain-2.8.0.klib
                |material-icons-core-commonMain-1.6.10.klib
                |material-ripple-commonMain-1.6.10.klib
                |material-ripple-nativeMain-1.6.10.klib
                |material3-commonMain-1.6.10.klib
                |material3-darwinMain-1.6.10.klib
                |material3-jsNativeMain-1.6.10.klib
                |material3-nativeMain-1.6.10.klib
                |material3-skikoMain-1.6.10.klib
                |runtime-commonMain-1.6.10.klib
                |runtime-jbMain-1.6.10.klib
                |runtime-jsNativeMain-1.6.10.klib
                |runtime-nativeMain-1.6.10.klib
                |runtime-saveable-commonMain-1.6.10.klib
                |runtime-uikitMain-1.6.10.klib
                |skiko-commonMain-0.8.4.klib
                |skiko-darwinMain-0.8.4.klib
                |skiko-iosMain-0.8.4.klib
                |skiko-nativeJsMain-0.8.4.klib
                |skiko-nativeMain-0.8.4.klib
                |ui-commonMain-1.6.10.klib
                |ui-darwinMain-1.6.10.klib
                |ui-geometry-commonMain-1.6.10.klib
                |ui-graphics-commonMain-1.6.10.klib
                |ui-graphics-jsNativeMain-1.6.10.klib
                |ui-graphics-nativeMain-1.6.10.klib
                |ui-graphics-skikoExcludingWebMain-1.6.10.klib
                |ui-graphics-skikoMain-1.6.10.klib
                |ui-jsNativeMain-1.6.10.klib
                |ui-nativeMain-1.6.10.klib
                |ui-skikoMain-1.6.10.klib
                |ui-text-commonMain-1.6.10.klib
                |ui-text-darwinMain-1.6.10.klib
                |ui-text-jsNativeMain-1.6.10.klib
                |ui-text-nativeMain-1.6.10.klib
                |ui-text-skikoMain-1.6.10.klib
                |ui-uikit-uikitMain-1.6.10.klib
                |ui-uikitMain-1.6.10.klib
                |ui-unit-commonMain-1.6.10.klib
                |ui-unit-jbMain-1.6.10.klib
                |ui-unit-jsNativeMain-1.6.10.klib
                |ui-util-commonMain-1.6.10.klib
                |ui-util-uikitMain-1.6.10.klib
                """.trimMargin(),
                sharedIosFragmentDeps
            )
    }

    @Test
    fun `test shared@iosX64 dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform")
        val iosAppIosX64FragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL),
                module = "shared",
                fragment = "iosX64",
                expected = """Fragment 'shared.iosX64' dependencies
+--- dep:shared:iosX64:org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|         \--- org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21 (c)
+--- dep:shared:iosX64:org.jetbrains.compose.runtime:runtime:1.6.10
|    \--- org.jetbrains.compose.runtime:runtime:1.6.10
|         \--- org.jetbrains.compose.runtime:runtime-uikitx64:1.6.10
|              +--- org.jetbrains.compose.collection-internal:collection:1.6.10
|              |    \--- androidx.collection:collection:1.4.0
|              |         \--- androidx.collection:collection-iosx64:1.4.0
|              |              +--- androidx.annotation:annotation:1.7.0 -> 1.8.0
|              |              |    \--- androidx.annotation:annotation-iosx64:1.8.0
|              |              |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 2.0.21
|              |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2
|              |    \--- org.jetbrains.kotlinx:atomicfu-iosx64:0.23.2
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-iosx64:1.8.0
|                        +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
|                        \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
+--- dep:shared:iosX64:org.jetbrains.compose.foundation:foundation:1.6.10
|    \--- org.jetbrains.compose.foundation:foundation:1.6.10
|         \--- org.jetbrains.compose.foundation:foundation-uikitx64:1.6.10
|              +--- org.jetbrains.compose.animation:animation:1.6.10
|              |    \--- org.jetbrains.compose.animation:animation-uikitx64:1.6.10
|              |         +--- org.jetbrains.compose.animation:animation-core:1.6.10
|              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitx64:1.6.10
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10
|              |         |         |    \--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.6.10
|              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitx64:1.6.10
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0
|              |         |         |         |    \--- androidx.lifecycle:lifecycle-common:2.8.0
|              |         |         |         |         \--- androidx.lifecycle:lifecycle-common-iosx64:2.8.0
|              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              |         |         |         |              +--- org.jetbrains.kotlinx:atomicfu:0.17.0 -> 0.23.2 (*)
|              |         |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0
|              |         |         |         |    \--- androidx.lifecycle:lifecycle-runtime:2.8.0
|              |         |         |         |         \--- androidx.lifecycle:lifecycle-runtime-iosx64:2.8.0
|              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |              +--- androidx.lifecycle:lifecycle-common:2.8.0 (*)
|              |         |         |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.0
|              |         |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitx64:2.8.0
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0 (*)
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0 (*)
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.0
|              |         |         |         |    \--- androidx.lifecycle:lifecycle-viewmodel:2.8.0
|              |         |         |         |         \--- androidx.lifecycle:lifecycle-viewmodel-iosx64:2.8.0
|              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              |         |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-uikitx64:1.6.10
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.6.10
|              |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-uikit-uikitx64:1.6.10
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitx64:1.6.10
|              |         |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4
|              |         |         |         |              \--- org.jetbrains.skiko:skiko-iosx64:0.8.4
|              |         |         |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|              |         |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10
|              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitx64:1.6.10
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
|              +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              \--- org.jetbrains.skiko:skiko:0.8.4 (*)
\--- dep:shared:iosX64:org.jetbrains.compose.material3:material3:1.6.10
     \--- org.jetbrains.compose.material3:material3:1.6.10
          \--- org.jetbrains.compose.material3:material3-uikitx64:1.6.10
               +--- org.jetbrains.compose.animation:animation-core:1.6.10 (*)
               +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
               +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
               +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
               +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
               +--- org.jetbrains.compose.material:material-icons-core:1.6.10
               |    \--- org.jetbrains.compose.material:material-icons-core-uikitx64:1.6.10
               |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
               |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
               |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
               |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
               +--- org.jetbrains.compose.material:material-ripple:1.6.10
               |    \--- org.jetbrains.compose.material:material-ripple-uikitx64:1.6.10
               |         +--- org.jetbrains.compose.animation:animation:1.6.10 (*)
               |         +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
               |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
               +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
               +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
               +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
               +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
               \--- org.jetbrains.kotlinx:kotlinx-datetime:0.5.0
                    \--- org.jetbrains.kotlinx:kotlinx-datetime-iosx64:0.5.0
                         +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
                         |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-iosx64:1.6.2
                         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> 2.0.21
                         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
                         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
                  """.trimIndent()
            )
        }

        assertFiles(
            files = """
                |animation-core-uikitx64-1.6.10.klib
                |animation-uikitx64-1.6.10.klib
                |annotation-iosx64-1.8.0.klib
                |atomicfu-iosx64-0.23.2-cinterop-interop.klib
                |atomicfu-iosx64-0.23.2.klib
                |collection-iosx64-1.4.0.klib
                |foundation-layout-uikitx64-1.6.10.klib
                |foundation-uikitx64-1.6.10.klib
                |kotlinx-coroutines-core-iosx64-1.8.0.klib
                |kotlinx-datetime-iosx64-0.5.0.klib
                |kotlinx-serialization-core-iosx64-1.6.2.klib
                |lifecycle-common-iosx64-2.8.0.klib
                |lifecycle-runtime-compose-uikitx64-2.8.0.klib
                |lifecycle-runtime-iosx64-2.8.0.klib
                |lifecycle-viewmodel-iosx64-2.8.0.klib
                |material-icons-core-uikitx64-1.6.10.klib
                |material-ripple-uikitx64-1.6.10.klib
                |material3-uikitx64-1.6.10.klib
                |runtime-saveable-uikitx64-1.6.10.klib
                |runtime-uikitx64-1.6.10.klib
                |skiko-iosx64-0.8.4-cinterop-uikit.klib
                |skiko-iosx64-0.8.4.klib
                |ui-geometry-uikitx64-1.6.10.klib
                |ui-graphics-uikitx64-1.6.10.klib
                |ui-text-uikitx64-1.6.10.klib
                |ui-uikit-uikitx64-1.6.10-cinterop-utils.klib
                |ui-uikit-uikitx64-1.6.10.klib
                |ui-uikitx64-1.6.10.klib
                |ui-unit-uikitx64-1.6.10.klib
                |ui-util-uikitx64-1.6.10.klib
                """.trimMargin(),
            iosAppIosX64FragmentDeps
        )
    }

    @Test
    fun `test shared@iosX64Test dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform")
        val iosAppIosX64FragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL),
                module = "shared",
                fragment = "iosX64Test",
                expected = """Fragment 'shared.iosX64Test' dependencies
+--- dep:shared:iosX64Test:org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|         \--- org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21 (c)
+--- dep:shared:iosX64Test:org.jetbrains.kotlin:kotlin-test:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-test:2.0.21
+--- dep:shared:iosX64Test:org.jetbrains.kotlin:kotlin-test-annotations-common:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-test-annotations-common:2.0.21
+--- dep:shared:iosX64Test:org.jetbrains.compose.runtime:runtime:1.6.10
|    \--- org.jetbrains.compose.runtime:runtime:1.6.10
|         \--- org.jetbrains.compose.runtime:runtime-uikitx64:1.6.10
|              +--- org.jetbrains.compose.collection-internal:collection:1.6.10
|              |    \--- androidx.collection:collection:1.4.0
|              |         \--- androidx.collection:collection-iosx64:1.4.0
|              |              +--- androidx.annotation:annotation:1.7.0 -> 1.8.0
|              |              |    \--- androidx.annotation:annotation-iosx64:1.8.0
|              |              |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 2.0.21
|              |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2
|              |    \--- org.jetbrains.kotlinx:atomicfu-iosx64:0.23.2
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-iosx64:1.8.0
|                        +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
|                        \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
+--- dep:shared:iosX64Test:org.jetbrains.compose.foundation:foundation:1.6.10
|    \--- org.jetbrains.compose.foundation:foundation:1.6.10
|         \--- org.jetbrains.compose.foundation:foundation-uikitx64:1.6.10
|              +--- org.jetbrains.compose.animation:animation:1.6.10
|              |    \--- org.jetbrains.compose.animation:animation-uikitx64:1.6.10
|              |         +--- org.jetbrains.compose.animation:animation-core:1.6.10
|              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitx64:1.6.10
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10
|              |         |         |    \--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.6.10
|              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitx64:1.6.10
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0
|              |         |         |         |    \--- androidx.lifecycle:lifecycle-common:2.8.0
|              |         |         |         |         \--- androidx.lifecycle:lifecycle-common-iosx64:2.8.0
|              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              |         |         |         |              +--- org.jetbrains.kotlinx:atomicfu:0.17.0 -> 0.23.2 (*)
|              |         |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0
|              |         |         |         |    \--- androidx.lifecycle:lifecycle-runtime:2.8.0
|              |         |         |         |         \--- androidx.lifecycle:lifecycle-runtime-iosx64:2.8.0
|              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |              +--- androidx.lifecycle:lifecycle-common:2.8.0 (*)
|              |         |         |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.0
|              |         |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitx64:2.8.0
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0 (*)
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0 (*)
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.0
|              |         |         |         |    \--- androidx.lifecycle:lifecycle-viewmodel:2.8.0
|              |         |         |         |         \--- androidx.lifecycle:lifecycle-viewmodel-iosx64:2.8.0
|              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              |         |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-uikitx64:1.6.10
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.6.10
|              |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-uikit-uikitx64:1.6.10
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitx64:1.6.10
|              |         |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4
|              |         |         |         |              \--- org.jetbrains.skiko:skiko-iosx64:0.8.4
|              |         |         |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|              |         |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10
|              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitx64:1.6.10
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
|              +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              \--- org.jetbrains.skiko:skiko:0.8.4 (*)
+--- dep:shared:iosX64Test:org.jetbrains.compose.material3:material3:1.6.10
|    \--- org.jetbrains.compose.material3:material3:1.6.10
|         \--- org.jetbrains.compose.material3:material3-uikitx64:1.6.10
|              +--- org.jetbrains.compose.animation:animation-core:1.6.10 (*)
|              +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
|              +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
|              +--- org.jetbrains.compose.material:material-icons-core:1.6.10
|              |    \--- org.jetbrains.compose.material:material-icons-core-uikitx64:1.6.10
|              |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.compose.material:material-ripple:1.6.10
|              |    \--- org.jetbrains.compose.material:material-ripple-uikitx64:1.6.10
|              |         +--- org.jetbrains.compose.animation:animation:1.6.10 (*)
|              |         +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
|              |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              \--- org.jetbrains.kotlinx:kotlinx-datetime:0.5.0
|                   \--- org.jetbrains.kotlinx:kotlinx-datetime-iosx64:0.5.0
|                        +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
|                        |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-iosx64:1.6.2
|                        |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> 2.0.21
|                        |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|                        \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
\--- dep:shared:iosX64Test:org.tinylog:tinylog-api-kotlin:2.6.2
     \--- org.tinylog:tinylog-api-kotlin:2.6.2
          +--- org.jetbrains.kotlin:kotlin-stdlib:1.4.32 -> 2.0.21
          \--- org.tinylog:tinylog-api:2.6.2
                  """.trimIndent()
            )
        }

        assertFiles(
            files = """
                |animation-core-uikitx64-1.6.10.klib
                |animation-uikitx64-1.6.10.klib
                |annotation-iosx64-1.8.0.klib
                |atomicfu-iosx64-0.23.2-cinterop-interop.klib
                |atomicfu-iosx64-0.23.2.klib
                |collection-iosx64-1.4.0.klib
                |foundation-layout-uikitx64-1.6.10.klib
                |foundation-uikitx64-1.6.10.klib
                |kotlinx-coroutines-core-iosx64-1.8.0.klib
                |kotlinx-datetime-iosx64-0.5.0.klib
                |kotlinx-serialization-core-iosx64-1.6.2.klib
                |lifecycle-common-iosx64-2.8.0.klib
                |lifecycle-runtime-compose-uikitx64-2.8.0.klib
                |lifecycle-runtime-iosx64-2.8.0.klib
                |lifecycle-viewmodel-iosx64-2.8.0.klib
                |material-icons-core-uikitx64-1.6.10.klib
                |material-ripple-uikitx64-1.6.10.klib
                |material3-uikitx64-1.6.10.klib
                |runtime-saveable-uikitx64-1.6.10.klib
                |runtime-uikitx64-1.6.10.klib
                |skiko-iosx64-0.8.4-cinterop-uikit.klib
                |skiko-iosx64-0.8.4.klib
                |tinylog-api-2.6.2.jar
                |tinylog-api-kotlin-2.6.2.jar
                |ui-geometry-uikitx64-1.6.10.klib
                |ui-graphics-uikitx64-1.6.10.klib
                |ui-text-uikitx64-1.6.10.klib
                |ui-uikit-uikitx64-1.6.10-cinterop-utils.klib
                |ui-uikit-uikitx64-1.6.10.klib
                |ui-uikitx64-1.6.10.klib
                |ui-unit-uikitx64-1.6.10.klib
                |ui-util-uikitx64-1.6.10.klib
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
        val aom = getTestProjectModel("compose-multiplatform")
        val iosAppIosX64FragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL),
                module = "ios-app",
                fragment = "iosX64Test",
                expected = """Fragment 'ios-app.iosX64Test' dependencies
+--- dep:ios-app:iosX64Test:org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|         \--- org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21 (c)
+--- dep:ios-app:iosX64Test:org.jetbrains.kotlin:kotlin-test:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-test:2.0.21
+--- dep:ios-app:iosX64Test:org.jetbrains.kotlin:kotlin-test-annotations-common:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-test-annotations-common:2.0.21
+--- dep:ios-app:iosX64Test:org.jetbrains.compose.runtime:runtime:1.6.10
|    \--- org.jetbrains.compose.runtime:runtime:1.6.10
|         \--- org.jetbrains.compose.runtime:runtime-uikitx64:1.6.10
|              +--- org.jetbrains.compose.collection-internal:collection:1.6.10
|              |    \--- androidx.collection:collection:1.4.0
|              |         \--- androidx.collection:collection-iosx64:1.4.0
|              |              +--- androidx.annotation:annotation:1.7.0 -> 1.8.0
|              |              |    \--- androidx.annotation:annotation-iosx64:1.8.0
|              |              |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 2.0.21
|              |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2
|              |    \--- org.jetbrains.kotlinx:atomicfu-iosx64:0.23.2
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-iosx64:1.8.0
|                        +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
|                        \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
+--- dep:ios-app:iosX64Test:org.jetbrains.compose.foundation:foundation:1.6.10
|    \--- org.jetbrains.compose.foundation:foundation:1.6.10
|         \--- org.jetbrains.compose.foundation:foundation-uikitx64:1.6.10
|              +--- org.jetbrains.compose.animation:animation:1.6.10
|              |    \--- org.jetbrains.compose.animation:animation-uikitx64:1.6.10
|              |         +--- org.jetbrains.compose.animation:animation-core:1.6.10
|              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitx64:1.6.10
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10
|              |         |         |    \--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.6.10
|              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitx64:1.6.10
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0
|              |         |         |         |    \--- androidx.lifecycle:lifecycle-common:2.8.0
|              |         |         |         |         \--- androidx.lifecycle:lifecycle-common-iosx64:2.8.0
|              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              |         |         |         |              +--- org.jetbrains.kotlinx:atomicfu:0.17.0 -> 0.23.2 (*)
|              |         |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0
|              |         |         |         |    \--- androidx.lifecycle:lifecycle-runtime:2.8.0
|              |         |         |         |         \--- androidx.lifecycle:lifecycle-runtime-iosx64:2.8.0
|              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |              +--- androidx.lifecycle:lifecycle-common:2.8.0 (*)
|              |         |         |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.0
|              |         |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitx64:2.8.0
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0 (*)
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0 (*)
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.0
|              |         |         |         |    \--- androidx.lifecycle:lifecycle-viewmodel:2.8.0
|              |         |         |         |         \--- androidx.lifecycle:lifecycle-viewmodel-iosx64:2.8.0
|              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              |         |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-uikitx64:1.6.10
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.6.10
|              |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-uikit-uikitx64:1.6.10
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitx64:1.6.10
|              |         |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4
|              |         |         |         |              \--- org.jetbrains.skiko:skiko-iosx64:0.8.4
|              |         |         |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|              |         |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10
|              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitx64:1.6.10
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
|              +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              \--- org.jetbrains.skiko:skiko:0.8.4 (*)
\--- dep:ios-app:iosX64Test:org.jetbrains.compose.material3:material3:1.6.10
     \--- org.jetbrains.compose.material3:material3:1.6.10
          \--- org.jetbrains.compose.material3:material3-uikitx64:1.6.10
               +--- org.jetbrains.compose.animation:animation-core:1.6.10 (*)
               +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
               +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
               +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
               +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
               +--- org.jetbrains.compose.material:material-icons-core:1.6.10
               |    \--- org.jetbrains.compose.material:material-icons-core-uikitx64:1.6.10
               |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
               |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
               |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
               |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
               +--- org.jetbrains.compose.material:material-ripple:1.6.10
               |    \--- org.jetbrains.compose.material:material-ripple-uikitx64:1.6.10
               |         +--- org.jetbrains.compose.animation:animation:1.6.10 (*)
               |         +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
               |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
               +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
               +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
               +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
               +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
               \--- org.jetbrains.kotlinx:kotlinx-datetime:0.5.0
                    \--- org.jetbrains.kotlinx:kotlinx-datetime-iosx64:0.5.0
                         +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
                         |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-iosx64:1.6.2
                         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> 2.0.21
                         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
                         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
                  """.trimIndent()
            )
        }

        assertFiles(
            files = """
                |animation-core-uikitx64-1.6.10.klib
                |animation-uikitx64-1.6.10.klib
                |annotation-iosx64-1.8.0.klib
                |atomicfu-iosx64-0.23.2-cinterop-interop.klib
                |atomicfu-iosx64-0.23.2.klib
                |collection-iosx64-1.4.0.klib
                |foundation-layout-uikitx64-1.6.10.klib
                |foundation-uikitx64-1.6.10.klib
                |kotlinx-coroutines-core-iosx64-1.8.0.klib
                |kotlinx-datetime-iosx64-0.5.0.klib
                |kotlinx-serialization-core-iosx64-1.6.2.klib
                |lifecycle-common-iosx64-2.8.0.klib
                |lifecycle-runtime-compose-uikitx64-2.8.0.klib
                |lifecycle-runtime-iosx64-2.8.0.klib
                |lifecycle-viewmodel-iosx64-2.8.0.klib
                |material-icons-core-uikitx64-1.6.10.klib
                |material-ripple-uikitx64-1.6.10.klib
                |material3-uikitx64-1.6.10.klib
                |runtime-saveable-uikitx64-1.6.10.klib
                |runtime-uikitx64-1.6.10.klib
                |skiko-iosx64-0.8.4-cinterop-uikit.klib
                |skiko-iosx64-0.8.4.klib
                |ui-geometry-uikitx64-1.6.10.klib
                |ui-graphics-uikitx64-1.6.10.klib
                |ui-text-uikitx64-1.6.10.klib
                |ui-uikit-uikitx64-1.6.10-cinterop-utils.klib
                |ui-uikit-uikitx64-1.6.10.klib
                |ui-uikitx64-1.6.10.klib
                |ui-unit-uikitx64-1.6.10.klib
                |ui-util-uikitx64-1.6.10.klib
                """.trimMargin(),
            iosAppIosX64FragmentDeps
        )
    }

    @Test
    fun `test ios-app@ios dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform")

        val iosAppIosFragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL),
                module = "ios-app",
                fragment = "ios",
                expected = """Fragment 'ios-app.ios' dependencies
+--- dep:ios-app:ios:org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
+--- dep:ios-app:ios:org.jetbrains.compose.runtime:runtime:1.6.10
|    \--- org.jetbrains.compose.runtime:runtime:1.6.10
|         +--- org.jetbrains.kotlinx:atomicfu:0.23.2
|         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|         |    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|         |    +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
|         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|         \--- org.jetbrains.compose.collection-internal:collection:1.6.10
|              +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|              +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10
|              |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|              \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
+--- dep:ios-app:ios:org.jetbrains.compose.foundation:foundation:1.6.10
|    \--- org.jetbrains.compose.foundation:foundation:1.6.10
|         +--- org.jetbrains.compose.animation:animation:1.6.10
|         |    +--- org.jetbrains.compose.animation:animation-core:1.6.10
|         |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|         |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    +--- org.jetbrains.compose.ui:ui:1.6.10
|         |    |    |    +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.6.10
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    |    \--- org.jetbrains.compose.ui:ui-util:1.6.10
|         |    |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    |         \--- org.jetbrains.compose.ui:ui-uikit:1.6.10
|         |    |    |    +--- org.jetbrains.compose.ui:ui-graphics:1.6.10
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.6.10
|         |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|         |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         |    |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.skiko:skiko:0.8.4
|         |    |    |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|         |    |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|         |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-text:1.6.10
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.skiko:skiko:0.8.4 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|         |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.skiko:skiko:0.8.4 (*)
|         |    |    |    +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|         |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|         |    |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0 (*)
|         |    |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.0
|         |    |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0 (*)
|         |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0 (*)
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|         |    |    |    +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.0
|         |    |    |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|         |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|         |    |    |    \--- org.jetbrains.compose.ui:ui-uikit:1.6.10
|         |    |    +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|         |    |    +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    |    \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|         |    +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10
|         |    |    +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    |    +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         |    |    \--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         |    +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|         |    +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|         |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         |    \--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|         +--- org.jetbrains.skiko:skiko:0.8.4 (*)
|         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
|         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|         +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
|         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|         +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
|         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|         \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
\--- dep:ios-app:ios:org.jetbrains.compose.material3:material3:1.6.10
     \--- org.jetbrains.compose.material3:material3:1.6.10
          +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
          +--- org.jetbrains.compose.material:material-icons-core:1.6.10
          |    +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
          |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
          |    +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
          |    \--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
          +--- org.jetbrains.compose.material:material-ripple:1.6.10
          |    +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
          |    +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
          |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
          |    +--- org.jetbrains.compose.animation:animation:1.6.10 (*)
          |    \--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
          +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
          +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
          +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
          +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21 (*)
          +--- org.jetbrains.compose.animation:animation-core:1.6.10 (*)
          +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
          +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
          +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
          +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
          +--- org.jetbrains.kotlinx:kotlinx-datetime:0.5.0
          |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
          |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
          |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> 2.0.21 (*)
          |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
          \--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
             """.trimIndent()
            )
        }
        assertFiles(
            files = """
                |animation-commonMain-1.6.10.klib
                |animation-core-commonMain-1.6.10.klib
                |animation-core-jbMain-1.6.10.klib
                |animation-core-jsNativeMain-1.6.10.klib
                |animation-core-uikitMain-1.6.10.klib
                |animation-jsNativeMain-1.6.10.klib
                |animation-nativeMain-1.6.10.klib
                |annotation-commonMain-1.6.10.klib
                |annotation-nonJvmMain-1.6.10.klib
                |atomicfu-commonMain-0.23.2.klib
                |atomicfu-nativeMain-0.23.2.klib
                |collection-commonMain-1.6.10.klib
                |collection-jbMain-1.6.10.klib
                |collection-jsNativeMain-1.6.10.klib
                |foundation-commonMain-1.6.10.klib
                |foundation-darwinMain-1.6.10.klib
                |foundation-jsNativeMain-1.6.10.klib
                |foundation-layout-commonMain-1.6.10.klib
                |foundation-layout-jsNativeMain-1.6.10.klib
                |foundation-layout-skikoMain-1.6.10.klib
                |foundation-layout-uikitMain-1.6.10.klib
                |foundation-nativeMain-1.6.10.klib
                |foundation-skikoMain-1.6.10.klib
                |foundation-uikitMain-1.6.10.klib
                |kotlin-stdlib-commonMain-2.0.21.klib
                |kotlinx-coroutines-core-commonMain-1.8.0.klib
                |kotlinx-coroutines-core-concurrentMain-1.8.0.klib
                |kotlinx-coroutines-core-nativeDarwinMain-1.8.0.klib
                |kotlinx-coroutines-core-nativeMain-1.8.0.klib
                |kotlinx-datetime-commonMain-0.5.0.klib
                |kotlinx-datetime-darwinMain-0.5.0.klib
                |kotlinx-datetime-nativeMain-0.5.0.klib
                |kotlinx-serialization-core-commonMain-1.6.2.klib
                |kotlinx-serialization-core-nativeMain-1.6.2.klib
                |lifecycle-common-commonMain-2.8.0.klib
                |lifecycle-common-nonJvmMain-2.8.0.klib
                |lifecycle-runtime-commonMain-2.8.0.klib
                |lifecycle-runtime-compose-commonMain-2.8.0.klib
                |lifecycle-runtime-nativeMain-2.8.0.klib
                |lifecycle-runtime-nonJvmMain-2.8.0.klib
                |lifecycle-viewmodel-commonMain-2.8.0.klib
                |lifecycle-viewmodel-nativeMain-2.8.0.klib
                |lifecycle-viewmodel-nonJvmMain-2.8.0.klib
                |material-icons-core-commonMain-1.6.10.klib
                |material-ripple-commonMain-1.6.10.klib
                |material-ripple-nativeMain-1.6.10.klib
                |material3-commonMain-1.6.10.klib
                |material3-darwinMain-1.6.10.klib
                |material3-jsNativeMain-1.6.10.klib
                |material3-nativeMain-1.6.10.klib
                |material3-skikoMain-1.6.10.klib
                |runtime-commonMain-1.6.10.klib
                |runtime-jbMain-1.6.10.klib
                |runtime-jsNativeMain-1.6.10.klib
                |runtime-nativeMain-1.6.10.klib
                |runtime-saveable-commonMain-1.6.10.klib
                |runtime-uikitMain-1.6.10.klib
                |skiko-commonMain-0.8.4.klib
                |skiko-darwinMain-0.8.4.klib
                |skiko-iosMain-0.8.4.klib
                |skiko-nativeJsMain-0.8.4.klib
                |skiko-nativeMain-0.8.4.klib
                |ui-commonMain-1.6.10.klib
                |ui-darwinMain-1.6.10.klib
                |ui-geometry-commonMain-1.6.10.klib
                |ui-graphics-commonMain-1.6.10.klib
                |ui-graphics-jsNativeMain-1.6.10.klib
                |ui-graphics-nativeMain-1.6.10.klib
                |ui-graphics-skikoExcludingWebMain-1.6.10.klib
                |ui-graphics-skikoMain-1.6.10.klib
                |ui-jsNativeMain-1.6.10.klib
                |ui-nativeMain-1.6.10.klib
                |ui-skikoMain-1.6.10.klib
                |ui-text-commonMain-1.6.10.klib
                |ui-text-darwinMain-1.6.10.klib
                |ui-text-jsNativeMain-1.6.10.klib
                |ui-text-nativeMain-1.6.10.klib
                |ui-text-skikoMain-1.6.10.klib
                |ui-uikit-uikitMain-1.6.10.klib
                |ui-uikitMain-1.6.10.klib
                |ui-unit-commonMain-1.6.10.klib
                |ui-unit-jbMain-1.6.10.klib
                |ui-unit-jsNativeMain-1.6.10.klib
                |ui-util-commonMain-1.6.10.klib
                |ui-util-uikitMain-1.6.10.klib
                """.trimMargin(),
            iosAppIosFragmentDeps
        )
    }

    @Test
    fun `test ios-app@iosX64 dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform")
        val iosAppIosX64FragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL),
                module = "ios-app",
                fragment = "iosX64",
                expected = """Fragment 'ios-app.iosX64' dependencies
+--- dep:ios-app:iosX64:org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|         \--- org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21 (c)
+--- dep:ios-app:iosX64:org.jetbrains.compose.runtime:runtime:1.6.10
|    \--- org.jetbrains.compose.runtime:runtime:1.6.10
|         \--- org.jetbrains.compose.runtime:runtime-uikitx64:1.6.10
|              +--- org.jetbrains.compose.collection-internal:collection:1.6.10
|              |    \--- androidx.collection:collection:1.4.0
|              |         \--- androidx.collection:collection-iosx64:1.4.0
|              |              +--- androidx.annotation:annotation:1.7.0 -> 1.8.0
|              |              |    \--- androidx.annotation:annotation-iosx64:1.8.0
|              |              |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 2.0.21
|              |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2
|              |    \--- org.jetbrains.kotlinx:atomicfu-iosx64:0.23.2
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
|                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-iosx64:1.8.0
|                        +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
|                        \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
+--- dep:ios-app:iosX64:org.jetbrains.compose.foundation:foundation:1.6.10
|    \--- org.jetbrains.compose.foundation:foundation:1.6.10
|         \--- org.jetbrains.compose.foundation:foundation-uikitx64:1.6.10
|              +--- org.jetbrains.compose.animation:animation:1.6.10
|              |    \--- org.jetbrains.compose.animation:animation-uikitx64:1.6.10
|              |         +--- org.jetbrains.compose.animation:animation-core:1.6.10
|              |         |    \--- org.jetbrains.compose.animation:animation-core-uikitx64:1.6.10
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10
|              |         |         |    \--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.6.10
|              |         |         |    \--- org.jetbrains.compose.ui:ui-uikitx64:1.6.10
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0
|              |         |         |         |    \--- androidx.lifecycle:lifecycle-common:2.8.0
|              |         |         |         |         \--- androidx.lifecycle:lifecycle-common-iosx64:2.8.0
|              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              |         |         |         |              +--- org.jetbrains.kotlinx:atomicfu:0.17.0 -> 0.23.2 (*)
|              |         |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0
|              |         |         |         |    \--- androidx.lifecycle:lifecycle-runtime:2.8.0
|              |         |         |         |         \--- androidx.lifecycle:lifecycle-runtime-iosx64:2.8.0
|              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |              +--- androidx.lifecycle:lifecycle-common:2.8.0 (*)
|              |         |         |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.0
|              |         |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitx64:2.8.0
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0 (*)
|              |         |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0 (*)
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.0
|              |         |         |         |    \--- androidx.lifecycle:lifecycle-viewmodel:2.8.0
|              |         |         |         |         \--- androidx.lifecycle:lifecycle-viewmodel-iosx64:2.8.0
|              |         |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
|              |         |         |         |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21
|              |         |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-uikitx64:1.6.10
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.6.10
|              |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-uikit-uikitx64:1.6.10
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10
|              |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitx64:1.6.10
|              |         |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4
|              |         |         |         |              \--- org.jetbrains.skiko:skiko-iosx64:0.8.4
|              |         |         |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
|              |         |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.6.10
|              |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitx64:1.6.10
|              |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
|              |         +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10
|              |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitx64:1.6.10
|              |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|              |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
|              |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
|              +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
|              +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
|              +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
|              +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
|              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
|              +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
|              \--- org.jetbrains.skiko:skiko:0.8.4 (*)
\--- dep:ios-app:iosX64:org.jetbrains.compose.material3:material3:1.6.10
     \--- org.jetbrains.compose.material3:material3:1.6.10
          \--- org.jetbrains.compose.material3:material3-uikitx64:1.6.10
               +--- org.jetbrains.compose.animation:animation-core:1.6.10 (*)
               +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
               +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
               +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
               +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
               +--- org.jetbrains.compose.material:material-icons-core:1.6.10
               |    \--- org.jetbrains.compose.material:material-icons-core-uikitx64:1.6.10
               |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
               |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
               |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
               |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
               +--- org.jetbrains.compose.material:material-ripple:1.6.10
               |    \--- org.jetbrains.compose.material:material-ripple-uikitx64:1.6.10
               |         +--- org.jetbrains.compose.animation:animation:1.6.10 (*)
               |         +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
               |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
               +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
               +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
               +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 -> 2.0.21
               +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
               \--- org.jetbrains.kotlinx:kotlinx-datetime:0.5.0
                    \--- org.jetbrains.kotlinx:kotlinx-datetime-iosx64:0.5.0
                         +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
                         |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-iosx64:1.6.2
                         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> 2.0.21
                         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
                         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21
             """.trimIndent()
            )
        }

        assertFiles(
            files = """
                |animation-core-uikitx64-1.6.10.klib
                |animation-uikitx64-1.6.10.klib
                |annotation-iosx64-1.8.0.klib
                |atomicfu-iosx64-0.23.2-cinterop-interop.klib
                |atomicfu-iosx64-0.23.2.klib
                |collection-iosx64-1.4.0.klib
                |foundation-layout-uikitx64-1.6.10.klib
                |foundation-uikitx64-1.6.10.klib
                |kotlinx-coroutines-core-iosx64-1.8.0.klib
                |kotlinx-datetime-iosx64-0.5.0.klib
                |kotlinx-serialization-core-iosx64-1.6.2.klib
                |lifecycle-common-iosx64-2.8.0.klib
                |lifecycle-runtime-compose-uikitx64-2.8.0.klib
                |lifecycle-runtime-iosx64-2.8.0.klib
                |lifecycle-viewmodel-iosx64-2.8.0.klib
                |material-icons-core-uikitx64-1.6.10.klib
                |material-ripple-uikitx64-1.6.10.klib
                |material3-uikitx64-1.6.10.klib
                |runtime-saveable-uikitx64-1.6.10.klib
                |runtime-uikitx64-1.6.10.klib
                |skiko-iosx64-0.8.4-cinterop-uikit.klib
                |skiko-iosx64-0.8.4.klib
                |ui-geometry-uikitx64-1.6.10.klib
                |ui-graphics-uikitx64-1.6.10.klib
                |ui-text-uikitx64-1.6.10.klib
                |ui-uikit-uikitx64-1.6.10-cinterop-utils.klib
                |ui-uikit-uikitx64-1.6.10.klib
                |ui-uikitx64-1.6.10.klib
                |ui-unit-uikitx64-1.6.10.klib
                |ui-util-uikitx64-1.6.10.klib
                """.trimMargin(),
            iosAppIosX64FragmentDeps
        )
    }

    // todo (AB) : 'anrdoid-app.android' differs from what Gradle produce (versions).
    // todo (AB) : It seems it is caused by resolving RUNTIME version of library instead of COMPILE one being resolved by IdeSync.
    @Test
    fun `test android-app@android dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform")

        val androidAppAndroidFragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL),
                module = "android-app",
                fragment = "android",
                expected = """Fragment 'android-app.android' dependencies
+--- dep:android-app:android:androidx.activity:activity-compose:1.7.2
|    \--- androidx.activity:activity-compose:1.7.2
|         +--- androidx.activity:activity-ktx:1.7.2
|         |    +--- androidx.activity:activity:1.7.2
|         |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0
|         |    |    |    \--- androidx.annotation:annotation-jvm:1.8.0
|         |    |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 2.0.21
|         |    |    |              +--- org.jetbrains:annotations:13.0 -> 23.0.0
|         |    |    |              \--- org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21 (c)
|         |    |    +--- androidx.collection:collection:1.0.0 -> 1.4.0
|         |    |    |    \--- androidx.collection:collection-jvm:1.4.0
|         |    |    |         +--- androidx.annotation:annotation:1.7.0 -> 1.8.0 (*)
|         |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |    |    |         \--- androidx.collection:collection-ktx:1.4.0 (c)
|         |    |    +--- androidx.core:core:1.8.0 -> 1.12.0
|         |    |    |    +--- androidx.annotation:annotation:1.6.0 -> 1.8.0 (*)
|         |    |    |    +--- androidx.annotation:annotation-experimental:1.3.0 -> 1.4.0
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 2.0.21 (*)
|         |    |    |    +--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
|         |    |    |    +--- androidx.concurrent:concurrent-futures:1.0.0 -> 1.1.0
|         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |    |    |    |    \--- com.google.guava:listenablefuture:1.0
|         |    |    |    +--- androidx.interpolator:interpolator:1.0.0
|         |    |    |    |    \--- androidx.annotation:annotation:1.0.0 -> 1.8.0 (*)
|         |    |    |    +--- androidx.lifecycle:lifecycle-runtime:2.3.1 -> 2.8.0
|         |    |    |    |    \--- androidx.lifecycle:lifecycle-runtime-android:2.8.0
|         |    |    |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|         |    |    |    |         +--- androidx.arch.core:core-common:2.2.0
|         |    |    |    |         |    \--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |    |    |    |         +--- androidx.arch.core:core-runtime:2.2.0
|         |    |    |    |         |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |    |    |    |         |    \--- androidx.arch.core:core-common:2.2.0 (*)
|         |    |    |    |         +--- androidx.lifecycle:lifecycle-common:2.8.0
|         |    |    |    |         |    \--- androidx.lifecycle:lifecycle-common-jvm:2.8.0
|         |    |    |    |         |         +--- androidx.annotation:annotation:1.8.0 (*)
|         |    |    |    |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |    |    |    |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0
|         |    |    |    |         |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0
|         |    |    |    |         |         |         +--- org.jetbrains:annotations:23.0.0
|         |    |    |    |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.0
|         |    |    |    |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21 (*)
|         |    |    |    |         |         +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|         |    |    |    |         |         +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|         |    |    |    |         |         +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|         |    |    |    |         |         +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|         |    |    |    |         |         \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|         |    |    |    |         +--- androidx.profileinstaller:profileinstaller:1.3.1
|         |    |    |    |         |    +--- androidx.annotation:annotation:1.2.0 -> 1.8.0 (*)
|         |    |    |    |         |    +--- androidx.concurrent:concurrent-futures:1.1.0 (*)
|         |    |    |    |         |    +--- androidx.startup:startup-runtime:1.1.1
|         |    |    |    |         |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |    |    |    |         |    |    \--- androidx.tracing:tracing:1.0.0
|         |    |    |    |         |    |         \--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |    |    |    |         |    \--- com.google.guava:listenablefuture:1.0
|         |    |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |    |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3
|         |    |    |    |         |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|         |    |    |    |         |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.3 -> 1.8.0
|         |    |    |    |         |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 -> 2.0.21
|         |    |    |    |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21 (*)
|         |    |    |    |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21
|         |    |    |    |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21 (*)
|         |    |    |    |         +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|         |    |    |    |         +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|         |    |    |    |         +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|         |    |    |    |         +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|         |    |    |    |         \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|         |    |    |    +--- androidx.versionedparcelable:versionedparcelable:1.1.1
|         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |    |    |    |    \--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
|         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |    |    |    \--- androidx.core:core-ktx:1.12.0 (c)
|         |    |    +--- androidx.lifecycle:lifecycle-runtime:2.6.1 -> 2.8.0 (*)
|         |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 -> 2.8.0
|         |    |    |    \--- androidx.lifecycle:lifecycle-viewmodel-android:2.8.0
|         |    |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|         |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 (*)
|         |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|         |    |    |         +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|         |    |    |         +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|         |    |    |         +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|         |    |    |         +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|         |    |    |         \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|         |    |    +--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.1 -> 2.8.0
|         |    |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.8.0 (*)
|         |    |    |    +--- androidx.core:core-ktx:1.2.0 -> 1.12.0
|         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |    |    |    |    +--- androidx.core:core:1.12.0 (*)
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |    |    |    +--- androidx.lifecycle:lifecycle-livedata-core:2.8.0
|         |    |    |    |    +--- androidx.arch.core:core-common:2.2.0 (*)
|         |    |    |    |    +--- androidx.arch.core:core-runtime:2.2.0 (*)
|         |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.8.0 (*)
|         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |    |    |    |    +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|         |    |    |    |    +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|         |    |    |    |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|         |    |    |    |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|         |    |    |    |    \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|         |    |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.8.0 (*)
|         |    |    |    +--- androidx.savedstate:savedstate:1.2.1
|         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |    |    |    |    +--- androidx.arch.core:core-common:2.1.0 -> 2.2.0 (*)
|         |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.6.1 -> 2.8.0 (*)
|         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 2.0.21 (*)
|         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 (*)
|         |    |    |    +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|         |    |    |    +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|         |    |    |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|         |    |    |    \--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|         |    |    +--- androidx.profileinstaller:profileinstaller:1.3.0 -> 1.3.1 (*)
|         |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
|         |    |    +--- androidx.tracing:tracing:1.0.0 (*)
|         |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 2.0.21 (*)
|         |    +--- androidx.core:core-ktx:1.1.0 -> 1.12.0 (*)
|         |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.6.1 -> 2.8.0
|         |    |    \--- androidx.lifecycle:lifecycle-runtime-ktx-android:2.8.0
|         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|         |    |         +--- androidx.lifecycle:lifecycle-runtime:2.8.0 (*)
|         |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 (*)
|         |    |         +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|         |    |         +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|         |    |         +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|         |    |         \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|         |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1 -> 2.8.0
|         |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.8.0 (*)
|         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 (*)
|         |    |    +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|         |    |    +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|         |    |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|         |    |    \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|         |    +--- androidx.savedstate:savedstate-ktx:1.2.1
|         |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
|         |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 2.0.21 (*)
|         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 2.0.21 (*)
|         +--- androidx.compose.runtime:runtime:1.0.1 -> 1.6.7
|         |    \--- androidx.compose.runtime:runtime-android:1.6.7
|         |         +--- androidx.collection:collection:1.4.0 (*)
|         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1 -> 1.7.3 (*)
|         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 -> 1.8.0 (*)
|         +--- androidx.compose.runtime:runtime-saveable:1.0.1 -> 1.6.7
|         |    \--- androidx.compose.runtime:runtime-saveable-android:1.6.7
|         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|         +--- androidx.compose.ui:ui:1.0.1 -> 1.6.7
|         |    \--- androidx.compose.ui:ui-android:1.6.7
|         |         +--- androidx.activity:activity-ktx:1.7.0 -> 1.7.2 (*)
|         |         +--- androidx.annotation:annotation:1.6.0 -> 1.8.0 (*)
|         |         +--- androidx.autofill:autofill:1.0.0
|         |         |    \--- androidx.core:core:1.1.0 -> 1.12.0 (*)
|         |         +--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
|         |         +--- androidx.collection:collection:1.4.0 (*)
|         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|         |         +--- androidx.compose.runtime:runtime-saveable:1.6.7 (*)
|         |         +--- androidx.compose.ui:ui-geometry:1.6.7
|         |         |    \--- androidx.compose.ui:ui-geometry-android:1.6.7
|         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         +--- androidx.compose.runtime:runtime:1.2.1 -> 1.6.7 (*)
|         |         |         +--- androidx.compose.ui:ui-util:1.6.7
|         |         |         |    \--- androidx.compose.ui:ui-util-android:1.6.7
|         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|         |         +--- androidx.compose.ui:ui-graphics:1.6.7
|         |         |    \--- androidx.compose.ui:ui-graphics-android:1.6.7
|         |         |         +--- androidx.annotation:annotation:1.7.0 -> 1.8.0 (*)
|         |         |         +--- androidx.collection:collection:1.4.0 (*)
|         |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|         |         |         +--- androidx.compose.ui:ui-unit:1.6.7
|         |         |         |    \--- androidx.compose.ui:ui-unit-android:1.6.7
|         |         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         |         +--- androidx.collection:collection-ktx:1.2.0 -> 1.4.0
|         |         |         |         |    \--- androidx.collection:collection:1.4.0 (*)
|         |         |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|         |         |         |         +--- androidx.compose.ui:ui-geometry:1.6.7 (*)
|         |         |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|         |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|         |         +--- androidx.compose.ui:ui-text:1.6.7
|         |         |    \--- androidx.compose.ui:ui-text-android:1.6.7
|         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|         |         |         +--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
|         |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|         |         |         +--- androidx.compose.runtime:runtime-saveable:1.6.7 (*)
|         |         |         +--- androidx.compose.ui:ui-graphics:1.6.7 (*)
|         |         |         +--- androidx.compose.ui:ui-unit:1.6.7 (*)
|         |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|         |         |         +--- androidx.core:core:1.7.0 -> 1.12.0 (*)
|         |         |         +--- androidx.emoji2:emoji2:1.2.0 -> 1.3.0
|         |         |         |    +--- androidx.annotation:annotation:1.2.0 -> 1.8.0 (*)
|         |         |         |    +--- androidx.collection:collection:1.1.0 -> 1.4.0 (*)
|         |         |         |    +--- androidx.core:core:1.3.0 -> 1.12.0 (*)
|         |         |         |    +--- androidx.lifecycle:lifecycle-process:2.4.1 -> 2.8.0
|         |         |         |    |    +--- androidx.annotation:annotation:1.2.0 -> 1.8.0 (*)
|         |         |         |    |    +--- androidx.lifecycle:lifecycle-runtime:2.8.0 (*)
|         |         |         |    |    +--- androidx.startup:startup-runtime:1.1.1 (*)
|         |         |         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |         |         |    |    +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|         |         |         |    |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|         |         |         |    |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|         |         |         |    |    \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|         |         |         |    \--- androidx.startup:startup-runtime:1.0.0 -> 1.1.1 (*)
|         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 -> 1.8.0 (*)
|         |         +--- androidx.compose.ui:ui-unit:1.6.7 (*)
|         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|         |         +--- androidx.core:core:1.12.0 (*)
|         |         +--- androidx.customview:customview-poolingcontainer:1.0.0
|         |         |    +--- androidx.core:core-ktx:1.5.0 -> 1.12.0 (*)
|         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 2.0.21 (*)
|         |         +--- androidx.emoji2:emoji2:1.2.0 -> 1.3.0 (*)
|         |         +--- androidx.lifecycle:lifecycle-runtime:2.6.1 -> 2.8.0 (*)
|         |         +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 -> 2.8.0 (*)
|         |         +--- androidx.profileinstaller:profileinstaller:1.3.0 -> 1.3.1 (*)
|         |         +--- androidx.savedstate:savedstate-ktx:1.2.1 (*)
|         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1 -> 1.7.3 (*)
|         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 -> 1.8.0 (*)
|         +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 -> 2.8.0 (*)
|         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 2.0.21 (*)
+--- dep:android-app:android:org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21 (*)
+--- dep:android-app:android:org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21 (*)
+--- dep:android-app:android:org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21 (*)
+--- dep:android-app:android:org.jetbrains.compose.runtime:runtime:1.6.10
|    \--- org.jetbrains.compose.runtime:runtime:1.6.10
|         \--- androidx.compose.runtime:runtime:1.6.7 (*)
+--- dep:android-app:android:org.jetbrains.compose.foundation:foundation:1.6.10
|    \--- org.jetbrains.compose.foundation:foundation:1.6.10
|         \--- androidx.compose.foundation:foundation:1.6.7
|              \--- androidx.compose.foundation:foundation-android:1.6.7
|                   +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   +--- androidx.collection:collection:1.4.0 (*)
|                   +--- androidx.compose.animation:animation:1.6.7
|                   |    \--- androidx.compose.animation:animation-android:1.6.7
|                   |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         +--- androidx.compose.animation:animation-core:1.6.7
|                   |         |    \--- androidx.compose.animation:animation-core-android:1.6.7
|                   |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         +--- androidx.collection:collection:1.4.0 (*)
|                   |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|                   |         |         +--- androidx.compose.ui:ui:1.6.7 (*)
|                   |         |         +--- androidx.compose.ui:ui-unit:1.6.7 (*)
|                   |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|                   |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 -> 1.8.0 (*)
|                   |         +--- androidx.compose.foundation:foundation-layout:1.6.7
|                   |         |    \--- androidx.compose.foundation:foundation-layout-android:1.6.7
|                   |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         +--- androidx.compose.animation:animation-core:1.2.1 -> 1.6.7 (*)
|                   |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|                   |         |         +--- androidx.compose.ui:ui:1.6.7 (*)
|                   |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|                   |         |         +--- androidx.core:core:1.7.0 -> 1.12.0 (*)
|                   |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|                   |         +--- androidx.compose.ui:ui:1.6.7 (*)
|                   |         +--- androidx.compose.ui:ui-geometry:1.6.7 (*)
|                   |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|                   |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   +--- androidx.compose.foundation:foundation-layout:1.6.7 (*)
|                   +--- androidx.compose.runtime:runtime:1.6.7 (*)
|                   +--- androidx.compose.ui:ui:1.6.7 (*)
|                   +--- androidx.compose.ui:ui-text:1.6.7 (*)
|                   +--- androidx.compose.ui:ui-util:1.6.7 (*)
|                   +--- androidx.core:core:1.12.0 (*)
|                   +--- androidx.emoji2:emoji2:1.3.0 (*)
|                   \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
\--- dep:android-app:android:org.jetbrains.compose.material3:material3:1.6.10
     \--- org.jetbrains.compose.material3:material3:1.6.10
          \--- androidx.compose.material3:material3:1.2.1
               \--- androidx.compose.material3:material3-android:1.2.1
                    +--- androidx.activity:activity-compose:1.5.0 -> 1.7.2 (*)
                    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
                    +--- androidx.annotation:annotation-experimental:1.4.0 (*)
                    +--- androidx.collection:collection:1.4.0 (*)
                    +--- androidx.compose.animation:animation-core:1.6.0 -> 1.6.7 (*)
                    +--- androidx.compose.foundation:foundation:1.6.0 -> 1.6.7 (*)
                    +--- androidx.compose.foundation:foundation-layout:1.6.0 -> 1.6.7 (*)
                    +--- androidx.compose.material:material-icons-core:1.6.0
                    |    \--- androidx.compose.material:material-icons-core-android:1.6.0
                    |         +--- androidx.compose.ui:ui:1.6.0 -> 1.6.7 (*)
                    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
                    |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
                    +--- androidx.compose.material:material-ripple:1.6.0
                    |    \--- androidx.compose.material:material-ripple-android:1.6.0
                    |         +--- androidx.compose.animation:animation:1.6.0 -> 1.6.7 (*)
                    |         +--- androidx.compose.foundation:foundation:1.6.0 -> 1.6.7 (*)
                    |         +--- androidx.compose.runtime:runtime:1.6.0 -> 1.6.7 (*)
                    |         +--- androidx.compose.ui:ui-util:1.6.0 -> 1.6.7 (*)
                    |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
                    +--- androidx.compose.runtime:runtime:1.6.0 -> 1.6.7 (*)
                    +--- androidx.compose.ui:ui-graphics:1.6.0 -> 1.6.7 (*)
                    +--- androidx.compose.ui:ui-text:1.6.0 -> 1.6.7 (*)
                    +--- androidx.compose.ui:ui-util:1.6.0 -> 1.6.7 (*)
                    +--- androidx.lifecycle:lifecycle-common-java8:2.6.1 -> 2.8.0
                    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
                    |    +--- androidx.lifecycle:lifecycle-common:2.8.0 (*)
                    |    +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
                    |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
                    |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
                    |    \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
                    +--- androidx.lifecycle:lifecycle-runtime:2.6.1 -> 2.8.0 (*)
                    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 -> 2.8.0 (*)
                    +--- androidx.savedstate:savedstate-ktx:1.2.1 (*)
                    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
             """.trimIndent()
            )
        }
        // todo (AB) : Some versions are incorrect (?) - check difference with Gradle
        assertFiles(
            files = """
                |activity-1.7.2.aar
                |activity-compose-1.7.2.aar
                |activity-ktx-1.7.2.aar
                |animation-android-1.6.7.aar
                |animation-core-android-1.6.7.aar
                |annotation-experimental-1.4.0.aar
                |annotation-jvm-1.8.0.jar
                |annotations-23.0.0.jar
                |autofill-1.0.0.aar
                |collection-jvm-1.4.0.jar
                |collection-ktx-1.4.0.jar
                |concurrent-futures-1.1.0.jar
                |core-1.12.0.aar
                |core-common-2.2.0.jar
                |core-ktx-1.12.0.aar
                |core-runtime-2.2.0.aar
                |customview-poolingcontainer-1.0.0.aar
                |emoji2-1.3.0.aar
                |foundation-android-1.6.7.aar
                |foundation-layout-android-1.6.7.aar
                |interpolator-1.0.0.aar
                |kotlin-stdlib-2.0.21.jar
                |kotlin-stdlib-jdk7-2.0.21.jar
                |kotlin-stdlib-jdk8-2.0.21.jar
                |kotlinx-coroutines-android-1.7.3.jar
                |kotlinx-coroutines-core-jvm-1.8.0.jar
                |lifecycle-common-java8-2.8.0.jar
                |lifecycle-common-jvm-2.8.0.jar
                |lifecycle-livedata-core-2.8.0.aar
                |lifecycle-process-2.8.0.aar
                |lifecycle-runtime-android-2.8.0.aar
                |lifecycle-runtime-ktx-android-2.8.0.aar
                |lifecycle-viewmodel-android-2.8.0.aar
                |lifecycle-viewmodel-ktx-2.8.0.aar
                |lifecycle-viewmodel-savedstate-2.8.0.aar
                |listenablefuture-1.0.jar
                |material-icons-core-android-1.6.0.aar
                |material-ripple-android-1.6.0.aar
                |material3-android-1.2.1.aar
                |profileinstaller-1.3.1.aar
                |runtime-android-1.6.7.aar
                |runtime-saveable-android-1.6.7.aar
                |savedstate-1.2.1.aar
                |savedstate-ktx-1.2.1.aar
                |startup-runtime-1.1.1.aar
                |tracing-1.0.0.aar
                |ui-android-1.6.7.aar
                |ui-geometry-android-1.6.7.aar
                |ui-graphics-android-1.6.7.aar
                |ui-text-android-1.6.7.aar
                |ui-unit-android-1.6.7.aar
                |ui-util-android-1.6.7.aar
                |versionedparcelable-1.1.1.aar
                """.trimMargin(),
            androidAppAndroidFragmentDeps
        )
    }

    @Test
    fun `test shared@android dependencies graph`() {
        val aom = getTestProjectModel("compose-multiplatform")

        val sharedAndroidFragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL),
                module = "shared",
                fragment = "android",
                expected = """Fragment 'shared.android' dependencies
+--- dep:shared:android:org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
|         +--- org.jetbrains:annotations:13.0 -> 23.0.0
|         \--- org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21 (c)
+--- dep:shared:android:org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21
|         \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21 (*)
+--- dep:shared:android:org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21
|    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21
|         +--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21 (*)
|         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21 (*)
+--- dep:shared:android:org.jetbrains.compose.runtime:runtime:1.6.10
|    \--- org.jetbrains.compose.runtime:runtime:1.6.10
|         \--- androidx.compose.runtime:runtime:1.6.7
|              \--- androidx.compose.runtime:runtime-android:1.6.7
|                   +--- androidx.collection:collection:1.4.0
|                   |    \--- androidx.collection:collection-jvm:1.4.0
|                   |         +--- androidx.annotation:annotation:1.7.0 -> 1.8.0
|                   |         |    \--- androidx.annotation:annotation-jvm:1.8.0
|                   |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 2.0.21 (*)
|                   |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         \--- androidx.collection:collection-ktx:1.4.0 (c)
|                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1 -> 1.7.3
|                   |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0
|                   |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0
|                   |    |         +--- org.jetbrains:annotations:23.0.0
|                   |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.0
|                   |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.0.21 (*)
|                   |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.3 -> 1.8.0
|                   |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 -> 2.0.21 (*)
|                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 -> 1.8.0 (*)
+--- dep:shared:android:org.jetbrains.compose.foundation:foundation:1.6.10
|    \--- org.jetbrains.compose.foundation:foundation:1.6.10
|         \--- androidx.compose.foundation:foundation:1.6.7
|              \--- androidx.compose.foundation:foundation-android:1.6.7
|                   +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   +--- androidx.collection:collection:1.4.0 (*)
|                   +--- androidx.compose.animation:animation:1.6.7
|                   |    \--- androidx.compose.animation:animation-android:1.6.7
|                   |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         +--- androidx.compose.animation:animation-core:1.6.7
|                   |         |    \--- androidx.compose.animation:animation-core-android:1.6.7
|                   |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         +--- androidx.collection:collection:1.4.0 (*)
|                   |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|                   |         |         +--- androidx.compose.ui:ui:1.6.7
|                   |         |         |    \--- androidx.compose.ui:ui-android:1.6.7
|                   |         |         |         +--- androidx.activity:activity-ktx:1.7.0 -> 1.7.2
|                   |         |         |         |    +--- androidx.activity:activity:1.7.2
|                   |         |         |         |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         |         |    |    +--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
|                   |         |         |         |    |    +--- androidx.core:core:1.8.0 -> 1.12.0
|                   |         |         |         |    |    |    +--- androidx.annotation:annotation:1.6.0 -> 1.8.0 (*)
|                   |         |         |         |    |    |    +--- androidx.annotation:annotation-experimental:1.3.0 -> 1.4.0
|                   |         |         |         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 2.0.21 (*)
|                   |         |         |         |    |    |    +--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
|                   |         |         |         |    |    |    +--- androidx.concurrent:concurrent-futures:1.0.0 -> 1.1.0
|                   |         |         |         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         |         |    |    |    |    \--- com.google.guava:listenablefuture:1.0
|                   |         |         |         |    |    |    +--- androidx.interpolator:interpolator:1.0.0
|                   |         |         |         |    |    |    |    \--- androidx.annotation:annotation:1.0.0 -> 1.8.0 (*)
|                   |         |         |         |    |    |    +--- androidx.lifecycle:lifecycle-runtime:2.3.1 -> 2.8.0
|                   |         |         |         |    |    |    |    \--- androidx.lifecycle:lifecycle-runtime-android:2.8.0
|                   |         |         |         |    |    |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|                   |         |         |         |    |    |    |         +--- androidx.arch.core:core-common:2.2.0
|                   |         |         |         |    |    |    |         |    \--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         |         |    |    |    |         +--- androidx.arch.core:core-runtime:2.2.0
|                   |         |         |         |    |    |    |         |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         |         |    |    |    |         |    \--- androidx.arch.core:core-common:2.2.0 (*)
|                   |         |         |         |    |    |    |         +--- androidx.lifecycle:lifecycle-common:2.8.0
|                   |         |         |         |    |    |    |         |    \--- androidx.lifecycle:lifecycle-common-jvm:2.8.0
|                   |         |         |         |    |    |    |         |         +--- androidx.annotation:annotation:1.8.0 (*)
|                   |         |         |         |    |    |    |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |    |    |    |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|                   |         |         |         |    |    |    |         |         +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|                   |         |         |         |    |    |    |         |         +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|                   |         |         |         |    |    |    |         |         +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|                   |         |         |         |    |    |    |         |         +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|                   |         |         |         |    |    |    |         |         \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|                   |         |         |         |    |    |    |         +--- androidx.profileinstaller:profileinstaller:1.3.1
|                   |         |         |         |    |    |    |         |    +--- androidx.annotation:annotation:1.2.0 -> 1.8.0 (*)
|                   |         |         |         |    |    |    |         |    +--- androidx.concurrent:concurrent-futures:1.1.0 (*)
|                   |         |         |         |    |    |    |         |    +--- androidx.startup:startup-runtime:1.1.1
|                   |         |         |         |    |    |    |         |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         |         |    |    |    |         |    |    \--- androidx.tracing:tracing:1.0.0
|                   |         |         |         |    |    |    |         |    |         \--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         |         |    |    |    |         |    \--- com.google.guava:listenablefuture:1.0
|                   |         |         |         |    |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |    |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 (*)
|                   |         |         |         |    |    |    |         +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|                   |         |         |         |    |    |    |         +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|                   |         |         |         |    |    |    |         +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|                   |         |         |         |    |    |    |         +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|                   |         |         |         |    |    |    |         \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|                   |         |         |         |    |    |    +--- androidx.versionedparcelable:versionedparcelable:1.1.1
|                   |         |         |         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         |         |    |    |    |    \--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
|                   |         |         |         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |    |    |    \--- androidx.core:core-ktx:1.12.0 (c)
|                   |         |         |         |    |    +--- androidx.lifecycle:lifecycle-runtime:2.6.1 -> 2.8.0 (*)
|                   |         |         |         |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 -> 2.8.0
|                   |         |         |         |    |    |    \--- androidx.lifecycle:lifecycle-viewmodel-android:2.8.0
|                   |         |         |         |    |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|                   |         |         |         |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 (*)
|                   |         |         |         |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
|                   |         |         |         |    |    |         +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|                   |         |         |         |    |    |         +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|                   |         |         |         |    |    |         +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|                   |         |         |         |    |    |         +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|                   |         |         |         |    |    |         \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|                   |         |         |         |    |    +--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.1 -> 2.8.0
|                   |         |         |         |    |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.8.0 (*)
|                   |         |         |         |    |    |    +--- androidx.core:core-ktx:1.2.0 -> 1.12.0
|                   |         |         |         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         |         |    |    |    |    +--- androidx.core:core:1.12.0 (*)
|                   |         |         |         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |    |    |    +--- androidx.lifecycle:lifecycle-livedata-core:2.8.0
|                   |         |         |         |    |    |    |    +--- androidx.arch.core:core-common:2.2.0 (*)
|                   |         |         |         |    |    |    |    +--- androidx.arch.core:core-runtime:2.2.0 (*)
|                   |         |         |         |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.8.0 (*)
|                   |         |         |         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |    |    |    |    +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|                   |         |         |         |    |    |    |    +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|                   |         |         |         |    |    |    |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|                   |         |         |         |    |    |    |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|                   |         |         |         |    |    |    |    \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|                   |         |         |         |    |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.8.0 (*)
|                   |         |         |         |    |    |    +--- androidx.savedstate:savedstate:1.2.1
|                   |         |         |         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         |         |    |    |    |    +--- androidx.arch.core:core-common:2.1.0 -> 2.2.0 (*)
|                   |         |         |         |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.6.1 -> 2.8.0 (*)
|                   |         |         |         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 2.0.21 (*)
|                   |         |         |         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 (*)
|                   |         |         |         |    |    |    +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|                   |         |         |         |    |    |    +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|                   |         |         |         |    |    |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|                   |         |         |         |    |    |    \--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|                   |         |         |         |    |    +--- androidx.profileinstaller:profileinstaller:1.3.0 -> 1.3.1 (*)
|                   |         |         |         |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
|                   |         |         |         |    |    +--- androidx.tracing:tracing:1.0.0 (*)
|                   |         |         |         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 2.0.21 (*)
|                   |         |         |         |    |    \--- androidx.activity:activity-compose:1.7.2 (c)
|                   |         |         |         |    +--- androidx.core:core-ktx:1.1.0 -> 1.12.0 (*)
|                   |         |         |         |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.6.1 -> 2.8.0
|                   |         |         |         |    |    \--- androidx.lifecycle:lifecycle-runtime-ktx-android:2.8.0
|                   |         |         |         |    |         +--- androidx.annotation:annotation:1.8.0 (*)
|                   |         |         |         |    |         +--- androidx.lifecycle:lifecycle-runtime:2.8.0 (*)
|                   |         |         |         |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 (*)
|                   |         |         |         |    |         +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|                   |         |         |         |    |         +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|                   |         |         |         |    |         +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|                   |         |         |         |    |         \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|                   |         |         |         |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1 -> 2.8.0
|                   |         |         |         |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.8.0 (*)
|                   |         |         |         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 (*)
|                   |         |         |         |    |    +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|                   |         |         |         |    |    +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
|                   |         |         |         |    |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|                   |         |         |         |    |    \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|                   |         |         |         |    +--- androidx.savedstate:savedstate-ktx:1.2.1
|                   |         |         |         |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
|                   |         |         |         |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 2.0.21 (*)
|                   |         |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 2.0.21 (*)
|                   |         |         |         |    \--- androidx.activity:activity-compose:1.7.2 (c)
|                   |         |         |         +--- androidx.annotation:annotation:1.6.0 -> 1.8.0 (*)
|                   |         |         |         +--- androidx.autofill:autofill:1.0.0
|                   |         |         |         |    \--- androidx.core:core:1.1.0 -> 1.12.0 (*)
|                   |         |         |         +--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
|                   |         |         |         +--- androidx.collection:collection:1.4.0 (*)
|                   |         |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|                   |         |         |         +--- androidx.compose.runtime:runtime-saveable:1.6.7
|                   |         |         |         |    \--- androidx.compose.runtime:runtime-saveable-android:1.6.7
|                   |         |         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|                   |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   |         |         |         +--- androidx.compose.ui:ui-geometry:1.6.7
|                   |         |         |         |    \--- androidx.compose.ui:ui-geometry-android:1.6.7
|                   |         |         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         |         |         +--- androidx.compose.runtime:runtime:1.2.1 -> 1.6.7 (*)
|                   |         |         |         |         +--- androidx.compose.ui:ui-util:1.6.7
|                   |         |         |         |         |    \--- androidx.compose.ui:ui-util-android:1.6.7
|                   |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   |         |         |         +--- androidx.compose.ui:ui-graphics:1.6.7
|                   |         |         |         |    \--- androidx.compose.ui:ui-graphics-android:1.6.7
|                   |         |         |         |         +--- androidx.annotation:annotation:1.7.0 -> 1.8.0 (*)
|                   |         |         |         |         +--- androidx.collection:collection:1.4.0 (*)
|                   |         |         |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|                   |         |         |         |         +--- androidx.compose.ui:ui-unit:1.6.7
|                   |         |         |         |         |    \--- androidx.compose.ui:ui-unit-android:1.6.7
|                   |         |         |         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         |         |         |         +--- androidx.collection:collection-ktx:1.2.0 -> 1.4.0
|                   |         |         |         |         |         |    \--- androidx.collection:collection:1.4.0 (*)
|                   |         |         |         |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|                   |         |         |         |         |         +--- androidx.compose.ui:ui-geometry:1.6.7 (*)
|                   |         |         |         |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|                   |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   |         |         |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|                   |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   |         |         |         +--- androidx.compose.ui:ui-text:1.6.7
|                   |         |         |         |    \--- androidx.compose.ui:ui-text-android:1.6.7
|                   |         |         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         |         |         +--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
|                   |         |         |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|                   |         |         |         |         +--- androidx.compose.runtime:runtime-saveable:1.6.7 (*)
|                   |         |         |         |         +--- androidx.compose.ui:ui-graphics:1.6.7 (*)
|                   |         |         |         |         +--- androidx.compose.ui:ui-unit:1.6.7 (*)
|                   |         |         |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|                   |         |         |         |         +--- androidx.core:core:1.7.0 -> 1.12.0 (*)
|                   |         |         |         |         +--- androidx.emoji2:emoji2:1.2.0 -> 1.3.0
|                   |         |         |         |         |    +--- androidx.annotation:annotation:1.2.0 -> 1.8.0 (*)
|                   |         |         |         |         |    +--- androidx.collection:collection:1.1.0 -> 1.4.0 (*)
|                   |         |         |         |         |    +--- androidx.core:core:1.3.0 -> 1.12.0 (*)
|                   |         |         |         |         |    +--- androidx.lifecycle:lifecycle-process:2.4.1 -> 2.8.0
|                   |         |         |         |         |    |    +--- androidx.annotation:annotation:1.2.0 -> 1.8.0 (*)
|                   |         |         |         |         |    |    +--- androidx.lifecycle:lifecycle-runtime:2.8.0 (*)
|                   |         |         |         |         |    |    +--- androidx.startup:startup-runtime:1.1.1 (*)
|                   |         |         |         |         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |         |    |    +--- androidx.lifecycle:lifecycle-common-java8:2.8.0 (c)
|                   |         |         |         |         |    |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
|                   |         |         |         |         |    |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
|                   |         |         |         |         |    |    \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
|                   |         |         |         |         |    \--- androidx.startup:startup-runtime:1.0.0 -> 1.1.1 (*)
|                   |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   |         |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 -> 1.8.0 (*)
|                   |         |         |         +--- androidx.compose.ui:ui-unit:1.6.7 (*)
|                   |         |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|                   |         |         |         +--- androidx.core:core:1.12.0 (*)
|                   |         |         |         +--- androidx.customview:customview-poolingcontainer:1.0.0
|                   |         |         |         |    +--- androidx.core:core-ktx:1.5.0 -> 1.12.0 (*)
|                   |         |         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 2.0.21 (*)
|                   |         |         |         +--- androidx.emoji2:emoji2:1.2.0 -> 1.3.0 (*)
|                   |         |         |         +--- androidx.lifecycle:lifecycle-runtime:2.6.1 -> 2.8.0 (*)
|                   |         |         |         +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 -> 2.8.0 (*)
|                   |         |         |         +--- androidx.profileinstaller:profileinstaller:1.3.0 -> 1.3.1 (*)
|                   |         |         |         +--- androidx.savedstate:savedstate-ktx:1.2.1 (*)
|                   |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1 -> 1.7.3 (*)
|                   |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 -> 1.8.0 (*)
|                   |         |         +--- androidx.compose.ui:ui-unit:1.6.7 (*)
|                   |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|                   |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
|                   |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 -> 1.8.0 (*)
|                   |         +--- androidx.compose.foundation:foundation-layout:1.6.7
|                   |         |    \--- androidx.compose.foundation:foundation-layout-android:1.6.7
|                   |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
|                   |         |         +--- androidx.compose.animation:animation-core:1.2.1 -> 1.6.7 (*)
|                   |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|                   |         |         +--- androidx.compose.ui:ui:1.6.7 (*)
|                   |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|                   |         |         +--- androidx.core:core:1.7.0 -> 1.12.0 (*)
|                   |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
|                   |         +--- androidx.compose.ui:ui:1.6.7 (*)
|                   |         +--- androidx.compose.ui:ui-geometry:1.6.7 (*)
|                   |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
|                   |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
|                   +--- androidx.compose.foundation:foundation-layout:1.6.7 (*)
|                   +--- androidx.compose.runtime:runtime:1.6.7 (*)
|                   +--- androidx.compose.ui:ui:1.6.7 (*)
|                   +--- androidx.compose.ui:ui-text:1.6.7 (*)
|                   +--- androidx.compose.ui:ui-util:1.6.7 (*)
|                   +--- androidx.core:core:1.12.0 (*)
|                   +--- androidx.emoji2:emoji2:1.3.0 (*)
|                   \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
\--- dep:shared:android:org.jetbrains.compose.material3:material3:1.6.10
     \--- org.jetbrains.compose.material3:material3:1.6.10
          \--- androidx.compose.material3:material3:1.2.1
               \--- androidx.compose.material3:material3-android:1.2.1
                    +--- androidx.activity:activity-compose:1.5.0 -> 1.7.2
                    |    +--- androidx.activity:activity-ktx:1.7.2 (*)
                    |    +--- androidx.compose.runtime:runtime:1.0.1 -> 1.6.7 (*)
                    |    +--- androidx.compose.runtime:runtime-saveable:1.0.1 -> 1.6.7 (*)
                    |    +--- androidx.compose.ui:ui:1.0.1 -> 1.6.7 (*)
                    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 -> 2.8.0 (*)
                    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 2.0.21 (*)
                    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
                    +--- androidx.annotation:annotation-experimental:1.4.0 (*)
                    +--- androidx.collection:collection:1.4.0 (*)
                    +--- androidx.compose.animation:animation-core:1.6.0 -> 1.6.7 (*)
                    +--- androidx.compose.foundation:foundation:1.6.0 -> 1.6.7 (*)
                    +--- androidx.compose.foundation:foundation-layout:1.6.0 -> 1.6.7 (*)
                    +--- androidx.compose.material:material-icons-core:1.6.0
                    |    \--- androidx.compose.material:material-icons-core-android:1.6.0
                    |         +--- androidx.compose.ui:ui:1.6.0 -> 1.6.7 (*)
                    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.21 (*)
                    |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
                    +--- androidx.compose.material:material-ripple:1.6.0
                    |    \--- androidx.compose.material:material-ripple-android:1.6.0
                    |         +--- androidx.compose.animation:animation:1.6.0 -> 1.6.7 (*)
                    |         +--- androidx.compose.foundation:foundation:1.6.0 -> 1.6.7 (*)
                    |         +--- androidx.compose.runtime:runtime:1.6.0 -> 1.6.7 (*)
                    |         +--- androidx.compose.ui:ui-util:1.6.0 -> 1.6.7 (*)
                    |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
                    +--- androidx.compose.runtime:runtime:1.6.0 -> 1.6.7 (*)
                    +--- androidx.compose.ui:ui-graphics:1.6.0 -> 1.6.7 (*)
                    +--- androidx.compose.ui:ui-text:1.6.0 -> 1.6.7 (*)
                    +--- androidx.compose.ui:ui-util:1.6.0 -> 1.6.7 (*)
                    +--- androidx.lifecycle:lifecycle-common-java8:2.6.1 -> 2.8.0
                    |    +--- androidx.annotation:annotation:1.1.0 -> 1.8.0 (*)
                    |    +--- androidx.lifecycle:lifecycle-common:2.8.0 (*)
                    |    +--- androidx.lifecycle:lifecycle-process:2.8.0 (c)
                    |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.0 (c)
                    |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0 (c)
                    |    \--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.0 (c)
                    +--- androidx.lifecycle:lifecycle-runtime:2.6.1 -> 2.8.0 (*)
                    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 -> 2.8.0 (*)
                    +--- androidx.savedstate:savedstate-ktx:1.2.1 (*)
                    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.21
             """.trimIndent()
            )
        }
        // todo (AB) : Some versions are incorrect (?) - check difference with Gradle
        assertFiles(
            files = """
                |activity-1.7.2.aar
                |activity-compose-1.7.2.aar
                |activity-ktx-1.7.2.aar
                |animation-android-1.6.7.aar
                |animation-core-android-1.6.7.aar
                |annotation-experimental-1.4.0.aar
                |annotation-jvm-1.8.0.jar
                |annotations-23.0.0.jar
                |autofill-1.0.0.aar
                |collection-jvm-1.4.0.jar
                |collection-ktx-1.4.0.jar
                |concurrent-futures-1.1.0.jar
                |core-1.12.0.aar
                |core-common-2.2.0.jar
                |core-ktx-1.12.0.aar
                |core-runtime-2.2.0.aar
                |customview-poolingcontainer-1.0.0.aar
                |emoji2-1.3.0.aar
                |foundation-android-1.6.7.aar
                |foundation-layout-android-1.6.7.aar
                |interpolator-1.0.0.aar
                |kotlin-stdlib-2.0.21.jar
                |kotlin-stdlib-jdk7-2.0.21.jar
                |kotlin-stdlib-jdk8-2.0.21.jar
                |kotlinx-coroutines-android-1.7.3.jar
                |kotlinx-coroutines-core-jvm-1.8.0.jar
                |lifecycle-common-java8-2.8.0.jar
                |lifecycle-common-jvm-2.8.0.jar
                |lifecycle-livedata-core-2.8.0.aar
                |lifecycle-process-2.8.0.aar
                |lifecycle-runtime-android-2.8.0.aar
                |lifecycle-runtime-ktx-android-2.8.0.aar
                |lifecycle-viewmodel-android-2.8.0.aar
                |lifecycle-viewmodel-ktx-2.8.0.aar
                |lifecycle-viewmodel-savedstate-2.8.0.aar
                |listenablefuture-1.0.jar
                |material-icons-core-android-1.6.0.aar
                |material-ripple-android-1.6.0.aar
                |material3-android-1.2.1.aar
                |profileinstaller-1.3.1.aar
                |runtime-android-1.6.7.aar
                |runtime-saveable-android-1.6.7.aar
                |savedstate-1.2.1.aar
                |savedstate-ktx-1.2.1.aar
                |startup-runtime-1.1.1.aar
                |tracing-1.0.0.aar
                |ui-android-1.6.7.aar
                |ui-geometry-android-1.6.7.aar
                |ui-graphics-android-1.6.7.aar
                |ui-text-android-1.6.7.aar
                |ui-unit-android-1.6.7.aar
                |ui-util-android-1.6.7.aar
                |versionedparcelable-1.1.1.aar
                """.trimMargin(),
            sharedAndroidFragmentDeps
        )
    }

    protected suspend fun doTest(
        aom: Model,
        resolutionInput: ResolutionInput,
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        module: String? = null,
        fragment: String? = null,
        filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }
    ): DependencyNode {
        val graph =
            with(moduleDependenciesResolver) {
                aom.modules.resolveDependencies(resolutionInput)
            }

//        graph.verifyGraphConnectivity()
        if (verifyMessages) {
            graph.distinctBfsSequence().forEach {
                val messages = it.messages.filterMessages()
                assertTrue(messages.isEmpty(), "There must be no messages for $it: $messages")
            }
        }

        val subGraph = when {
            module != null && fragment != null -> DependencyNodeHolder("Fragment '$module.$fragment' dependencies", graph.fragmentDeps(module, fragment), emptyContext {})
            module != null -> graph.moduleDeps(module)
            else -> graph
        }

        expected?.let {
            assertEquals(expected, subGraph)
        }
        return subGraph
    }

    private fun assertEquals(@Language("text") expected: String, root: DependencyNode) =
        kotlin.test.assertEquals(expected, root.prettyPrint().trimEnd())

    protected suspend fun downloadAndAssertFiles(
        files: String,
        root: DependencyNode,
        withSources: Boolean = false,
        checkAutoAddedDocumentation: Boolean = true
    ) {
        Resolver().downloadDependencies(root, withSources)
        assertFiles(
            files,
            root,
            withSources,
            checkExistence = true,
            checkAutoAddedDocumentation = checkAutoAddedDocumentation
        )
    }

    // todo (AB) : Reuse utility methods from dependence-resolution test module
    protected fun assertFiles(
        files: String, root: DependencyNode,
        withSources: Boolean = false,
        checkExistence: Boolean = false,// could be set to true only in case dependency files were downloaded by caller already
        checkAutoAddedDocumentation: Boolean = true // auto added documentation files are skipped fom check if this flag is false.
    ) {
        root.distinctBfsSequence()
            .filterIsInstance<MavenDependencyNode>()
            .flatMap { it.dependency.files(withSources) }
            .filterNot { !checkAutoAddedDocumentation && it.isAutoAddedDocumentation }
            .mapNotNull { runBlocking { it.getPath() } }
            .sortedBy { it.name }
            .toSet()
            .let {
                kotlin.test.assertEquals(files, it.joinToString("\n") { it.name })
                if (checkExistence) {
                    it.forEach {
                        check(it.exists()) {
                            "File $it was returned from dependency resolution, but is missing on disk"
                        }
                    }
                }
            }
    }

    private fun DependencyNode.moduleDeps(name: String): ModuleDependencyNodeWithModule = children
        .filterIsInstance<ModuleDependencyNodeWithModule>()
        .single { it.module.userReadableName == name }

    private fun DependencyNode.fragmentDeps(module: String, fragment: String) =
        moduleDeps(module).children
            .filterIsInstance<DirectFragmentDependencyNodeHolder>()
            .filter { it.fragment.name == fragment }

    private fun getTestProjectModel(testProjectName: String): Model {
        val projectPath = testDataRoot.resolve(testProjectName)
        val aom = with(object : ProblemReporterContext {
            override val problemReporter: ProblemReporter = TestProblemReporter()
        }) {
            val amperProjectContext =
                StandaloneAmperProjectContext.create(projectPath, null) ?: fail("Fails to create test project context")
            when (val result = SchemaBasedModelImport.getModel(amperProjectContext)) {
                is Result.Failure -> throw result.exception
                is Result.Success -> result.value
            }
        }
        return aom
    }

    companion object {
        internal val REDIRECTOR_MAVEN2 = listOf("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")

        fun List<Message>.defaultFilterMessages(): List<Message> =
            filter { "Downloaded " !in it.text && "Resolved " !in it.text }
    }
}
