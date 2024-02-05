/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.TestInfo
import java.util.*
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildGraphTest {

    @Test
    fun `org_jetbrains_kotlin kotlin-test 1_9_10`(testInfo: TestInfo) {
        doTest(
            testInfo,
            expected = """root
                |\--- org.jetbrains.kotlin:kotlin-test:1.9.10
                |     \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.10
                |          +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.10
                |          \--- org.jetbrains:annotations:13.0
            """.trimMargin()
        )
    }

    @Test
    fun `org_jetbrains_kotlin kotlin-test 1_9_20`(testInfo: TestInfo) {
        doTest(
            testInfo,
            expected = """root
                |\--- org.jetbrains.kotlin:kotlin-test:1.9.20
                |     \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20
                |          \--- org.jetbrains:annotations:13.0
            """.trimMargin()
        )
    }

    @Test
    fun `org_jetbrains_kotlinx kotlinx-coroutines-core 1_6_4`(testInfo: TestInfo) {
        doTest(
            testInfo,
            expected = """root
                |\--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4
                |     \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4
                |          +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
                |          +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21
                |          |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21
                |          |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.21
                |          |    |    \--- org.jetbrains:annotations:13.0
                |          |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21
                |          |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 (*)
                |          \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.21
            """.trimMargin()
        )
    }

    @Test
    fun `org_jetbrains_skiko skiko 0_7_85`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            repositories = REDIRECTOR_MAVEN2 + "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/public/p/compose/dev",
            verifyMessages = false,
            expected = """root
                |\--- org.jetbrains.skiko:skiko:0.7.85
                |     +--- org.jetbrains.skiko:skiko-android:0.7.85
                |     |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20
                |     |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20
                |     |         |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.20
                |     |         |    \--- org.jetbrains:annotations:13.0
                |     |         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.20
                |     |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20 (*)
                |     \--- org.jetbrains.skiko:skiko-awt:0.7.85
                |          \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 (*)
            """.trimMargin()
        )
        root.asSequence().forEach {
            assertTrue(
                it.messages.none { "Downloaded from" !in it.text && "More than a single variant provided" !in it.text },
                "There should be no messages for $it: ${it.messages}"
            )
        }
    }

    /**
     * TODO: org.jetbrains.skiko:skiko-android:0.7.85 should be absent for pure JVM
     */
    @Test
    fun `org_jetbrains_compose_desktop desktop-jvm-macos-arm64 1_5_10`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            repositories = REDIRECTOR_MAVEN2 + "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/public/p/compose/dev",
            verifyMessages = false,
            expected = """root
                |\--- org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:1.5.10
                |     \--- org.jetbrains.compose.desktop:desktop:1.5.10
                |          \--- org.jetbrains.compose.desktop:desktop-jvm:1.5.10
                |               +--- org.jetbrains.compose.foundation:foundation:1.5.10
                |               |    \--- org.jetbrains.compose.foundation:foundation-desktop:1.5.10
                |               |         +--- org.jetbrains.compose.animation:animation:1.5.10
                |               |         |    \--- org.jetbrains.compose.animation:animation-desktop:1.5.10
                |               |         |         +--- org.jetbrains.compose.animation:animation-core:1.5.10
                |               |         |         |    \--- org.jetbrains.compose.animation:animation-core-desktop:1.5.10
                |               |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4
                |               |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4
                |               |         |         |                   +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
                |               |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21 -> 1.8.20
                |               |         |         |                   |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20
                |               |         |         |                   |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.20
                |               |         |         |                   |    |    \--- org.jetbrains:annotations:13.0
                |               |         |         |                   |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.20
                |               |         |         |                   |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20 (*)
                |               |         |         |                   \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.21 -> 1.8.20
                |               |         |         +--- org.jetbrains.compose.foundation:foundation-layout:1.5.10
                |               |         |         |    \--- org.jetbrains.compose.foundation:foundation-layout-desktop:1.5.10
                |               |         |         |         \--- org.jetbrains.compose.ui:ui:1.5.10
                |               |         |         |              \--- org.jetbrains.compose.ui:ui-desktop:1.5.10
                |               |         |         |                   +--- org.jetbrains.compose.runtime:runtime-saveable:1.5.10
                |               |         |         |                   |    \--- org.jetbrains.compose.runtime:runtime-saveable-desktop:1.5.10
                |               |         |         |                   |         \--- org.jetbrains.compose.runtime:runtime:1.5.10
                |               |         |         |                   |              \--- org.jetbrains.compose.runtime:runtime-desktop:1.5.10
                |               |         |         |                   |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (*)
                |               |         |         |                   +--- org.jetbrains.compose.ui:ui-geometry:1.5.10
                |               |         |         |                   |    \--- org.jetbrains.compose.ui:ui-geometry-desktop:1.5.10
                |               |         |         |                   +--- org.jetbrains.compose.ui:ui-graphics:1.5.10
                |               |         |         |                   |    \--- org.jetbrains.compose.ui:ui-graphics-desktop:1.5.10
                |               |         |         |                   |         +--- org.jetbrains.compose.ui:ui-unit:1.5.10
                |               |         |         |                   |         |    \--- org.jetbrains.compose.ui:ui-unit-desktop:1.5.10
                |               |         |         |                   |         |         \--- org.jetbrains.compose.ui:ui-geometry:1.5.10 (*)
                |               |         |         |                   |         \--- org.jetbrains.skiko:skiko:0.7.85
                |               |         |         |                   |              +--- org.jetbrains.skiko:skiko-android:0.7.85
                |               |         |         |                   |              |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 (*)
                |               |         |         |                   |              \--- org.jetbrains.skiko:skiko-awt:0.7.85
                |               |         |         |                   |                   \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 (*)
                |               |         |         |                   +--- org.jetbrains.compose.ui:ui-text:1.5.10
                |               |         |         |                   |    \--- org.jetbrains.compose.ui:ui-text-desktop:1.5.10
                |               |         |         |                   |         +--- org.jetbrains.compose.ui:ui-graphics:1.5.10 (*)
                |               |         |         |                   |         +--- org.jetbrains.compose.ui:ui-unit:1.5.10 (*)
                |               |         |         |                   |         \--- org.jetbrains.skiko:skiko:0.7.85 (*)
                |               |         |         |                   +--- org.jetbrains.compose.ui:ui-unit:1.5.10 (*)
                |               |         |         |                   \--- org.jetbrains.skiko:skiko:0.7.85 (*)
                |               |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                |               |         |         +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                |               |         |         \--- org.jetbrains.compose.ui:ui-geometry:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                |               |         \--- org.jetbrains.skiko:skiko:0.7.85 (*)
                |               +--- org.jetbrains.compose.material:material:1.5.10
                |               |    \--- org.jetbrains.compose.material:material-desktop:1.5.10
                |               |         +--- org.jetbrains.compose.animation:animation-core:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.foundation:foundation:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.material:material-icons-core:1.5.10
                |               |         |    \--- org.jetbrains.compose.material:material-icons-core-desktop:1.5.10
                |               |         |         \--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.material:material-ripple:1.5.10
                |               |         |    \--- org.jetbrains.compose.material:material-ripple-desktop:1.5.10
                |               |         |         +--- org.jetbrains.compose.foundation:foundation:1.5.10 (*)
                |               |         |         \--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                |               |         \--- org.jetbrains.compose.ui:ui-text:1.5.10 (*)
                |               +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                |               +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                |               \--- org.jetbrains.compose.ui:ui-tooling-preview:1.5.10
                |                    \--- org.jetbrains.compose.ui:ui-tooling-preview-desktop:1.5.10
                |                         \--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
            """.trimMargin()
        )
        root.asSequence().forEach {
            assertTrue(
                it.messages.none { "Downloaded from" !in it.text && "More than a single variant provided" !in it.text },
                "There should be no messages for $it: ${it.messages}"
            )
        }
        root.asSequence()
            .mapNotNull { it as? MavenDependencyNode }
            .flatMap { it.dependency.files.values }
            .mapNotNull { it.path }
            .forEach {
                assertTrue(it.extension == "jar", "Only jar files are expected, got ${it.name}")
            }
    }

    @Test
    fun `androidx_annotation annotation 1_6_0`(testInfo: TestInfo) {
        doTest(
            testInfo,
            repositories = REDIRECTOR_MAVEN2 + "https://cache-redirector.jetbrains.com/maven.google.com",
            expected = """root
                |\--- androidx.annotation:annotation:1.6.0
                |     \--- androidx.annotation:annotation-jvm:1.6.0
                |          \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.0
                |               +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.0
                |               \--- org.jetbrains:annotations:13.0
            """.trimMargin()
        )
    }

    @Test
    fun `androidx_activity activity-compose 1_7_2`(testInfo: TestInfo) {
        doTest(
            testInfo,
            platform = "android",
            repositories = REDIRECTOR_MAVEN2 + "https://cache-redirector.jetbrains.com/maven.google.com",
            expected = """root
                |\--- androidx.activity:activity-compose:1.7.2
                |     +--- androidx.activity:activity-ktx:1.7.2
                |     |    +--- androidx.activity:activity:1.7.2
                |     |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    +--- androidx.core:core:1.8.0
                |     |    |    |    +--- androidx.annotation:annotation:1.2.0
                |     |    |    |    +--- androidx.annotation:annotation-experimental:1.1.0
                |     |    |    |    +--- androidx.lifecycle:lifecycle-runtime:2.3.1 -> 2.6.1
                |     |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |    |    +--- androidx.arch.core:core-common:2.2.0
                |     |    |    |    |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.6.1
                |     |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10
                |     |    |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.10
                |     |    |    |    |         \--- org.jetbrains:annotations:13.0
                |     |    |    |    \--- androidx.versionedparcelable:versionedparcelable:1.1.1
                |     |    |    |         +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |         \--- androidx.collection:collection:1.0.0
                |     |    |    |              \--- androidx.annotation:annotation:1.0.0 -> 1.2.0
                |     |    |    +--- androidx.lifecycle:lifecycle-runtime:2.6.1 (*)
                |     |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1
                |     |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    +--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.1
                |     |    |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.2.0
                |     |    |    |    +--- androidx.core:core-ktx:1.2.0
                |     |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.3.41 -> 1.8.10 (*)
                |     |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |    |    \--- androidx.core:core:1.2.0 -> 1.8.0 (*)
                |     |    |    |    +--- androidx.lifecycle:lifecycle-livedata-core:2.6.1
                |     |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.6.1
                |     |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 (*)
                |     |    |    |    +--- androidx.savedstate:savedstate:1.2.1
                |     |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4
                |     |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
                |     |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    +--- androidx.core:core-ktx:1.1.0 -> 1.2.0 (*)
                |     |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.6.1
                |     |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.2.0
                |     |    |    +--- androidx.lifecycle:lifecycle-runtime:2.6.1 (*)
                |     |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4
                |     |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1
                |     |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 (*)
                |     |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4
                |     |    +--- androidx.savedstate:savedstate-ktx:1.2.1
                |     |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
                |     |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     +--- androidx.compose.runtime:runtime:1.0.1
                |     |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.0 -> 1.6.4
                |     +--- androidx.compose.runtime:runtime-saveable:1.0.1
                |     |    +--- androidx.compose.runtime:runtime:1.0.1 (*)
                |     |    \--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     +--- androidx.compose.ui:ui:1.0.1
                |     |    +--- androidx.compose.runtime:runtime-saveable:1.0.1 (*)
                |     |    +--- androidx.compose.ui:ui-geometry:1.0.1
                |     |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    +--- androidx.compose.ui:ui-graphics:1.0.1
                |     |    |    +--- androidx.annotation:annotation:1.2.0
                |     |    |    \--- androidx.compose.ui:ui-unit:1.0.1
                |     |    |         +--- androidx.compose.ui:ui-geometry:1.0.1 (*)
                |     |    |         \--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    +--- androidx.compose.ui:ui-text:1.0.1
                |     |    |    +--- androidx.compose.ui:ui-graphics:1.0.1 (*)
                |     |    |    +--- androidx.compose.ui:ui-unit:1.0.1 (*)
                |     |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    +--- androidx.compose.ui:ui-unit:1.0.1 (*)
                |     |    \--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     \--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 (*)
            """.trimMargin()
        )
    }

    @Test
    fun `org_tinylog slf4j-tinylog 2_7_0-M1`(testInfo: TestInfo) {
        doTest(
            testInfo,
            expected = """root
                |\--- org.tinylog:slf4j-tinylog:2.7.0-M1
                |     +--- org.slf4j:slf4j-api:2.0.9
                |     \--- org.tinylog:tinylog-api:2.7.0-M1
            """.trimMargin()
        )
    }

    @Test
    fun `org_tinylog tinylog-api 2_7_0-M1`(testInfo: TestInfo) {
        doTest(
            testInfo,
            expected = """root
                |\--- org.tinylog:tinylog-api:2.7.0-M1
            """.trimMargin()
        )
    }

    @Test
    fun `androidx_appcompat appcompat 1_6_1`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            repositories = REDIRECTOR_MAVEN2 + "https://cache-redirector.jetbrains.com/dl.google.com/dl/android/maven2",
            expected = """root
                |\--- androidx.appcompat:appcompat:1.6.1
                |     +--- androidx.activity:activity:1.6.0
                |     |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    +--- androidx.core:core:1.8.0 -> 1.9.0
                |     |    |    +--- androidx.annotation:annotation:1.2.0 -> 1.3.0
                |     |    |    +--- androidx.annotation:annotation-experimental:1.3.0
                |     |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10
                |     |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.10
                |     |    |    |         \--- org.jetbrains:annotations:13.0
                |     |    |    +--- androidx.lifecycle:lifecycle-runtime:2.3.1 -> 2.5.1
                |     |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    |    +--- androidx.arch.core:core-common:2.1.0
                |     |    |    |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    |    \--- androidx.lifecycle:lifecycle-common:2.5.1
                |     |    |    |         \--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    \--- androidx.versionedparcelable:versionedparcelable:1.1.1
                |     |    |         +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |         \--- androidx.collection:collection:1.0.0 -> 1.1.0
                |     |    |              \--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    +--- androidx.lifecycle:lifecycle-runtime:2.5.1 (*)
                |     |    +--- androidx.lifecycle:lifecycle-viewmodel:2.5.1
                |     |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 1.7.10 (*)
                |     |    +--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.5.1
                |     |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |    |    +--- androidx.core:core-ktx:1.2.0
                |     |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.3.41 -> 1.7.10 (*)
                |     |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    |    \--- androidx.core:core:1.2.0 -> 1.9.0 (*)
                |     |    |    +--- androidx.lifecycle:lifecycle-livedata-core:2.5.1
                |     |    |    |    \--- androidx.lifecycle:lifecycle-common:2.5.1 (*)
                |     |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.5.1 (*)
                |     |    |    +--- androidx.savedstate:savedstate:1.2.0
                |     |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.20 -> 1.7.10 (*)
                |     |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 1.7.10 (*)
                |     |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.1
                |     |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1
                |     |    |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.1
                |     |    |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.1
                |     |    |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.0
                |     |    |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.6.0 -> 1.7.10 (*)
                |     |    |         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.0
                |     |    |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.0 -> 1.7.10 (*)
                |     |    |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.0 -> 1.7.10
                |     |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.1
                |     |    |         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.0 (*)
                |     |    +--- androidx.savedstate:savedstate:1.2.0 (*)
                |     |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 (*)
                |     +--- androidx.annotation:annotation:1.3.0
                |     +--- androidx.appcompat:appcompat-resources:1.6.1
                |     |    +--- androidx.annotation:annotation:1.2.0 -> 1.3.0
                |     |    +--- androidx.core:core:1.6.0 -> 1.9.0 (*)
                |     |    +--- androidx.vectordrawable:vectordrawable:1.1.0
                |     |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    +--- androidx.core:core:1.1.0 -> 1.9.0 (*)
                |     |    |    \--- androidx.collection:collection:1.1.0 (*)
                |     |    \--- androidx.vectordrawable:vectordrawable-animated:1.1.0
                |     |         +--- androidx.vectordrawable:vectordrawable:1.1.0 (*)
                |     |         +--- androidx.interpolator:interpolator:1.0.0
                |     |         |    \--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |         \--- androidx.collection:collection:1.1.0 (*)
                |     +--- androidx.core:core:1.9.0 (*)
                |     +--- androidx.cursoradapter:cursoradapter:1.0.0
                |     |    \--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     +--- androidx.drawerlayout:drawerlayout:1.0.0
                |     |    +--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |    +--- androidx.core:core:1.0.0 -> 1.9.0 (*)
                |     |    \--- androidx.customview:customview:1.0.0
                |     |         +--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |         \--- androidx.core:core:1.0.0 -> 1.9.0 (*)
                |     +--- androidx.fragment:fragment:1.3.6
                |     |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    +--- androidx.core:core:1.2.0 -> 1.9.0 (*)
                |     |    +--- androidx.collection:collection:1.1.0 (*)
                |     |    +--- androidx.viewpager:viewpager:1.0.0
                |     |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |    |    +--- androidx.core:core:1.0.0 -> 1.9.0 (*)
                |     |    |    \--- androidx.customview:customview:1.0.0 (*)
                |     |    +--- androidx.loader:loader:1.0.0
                |     |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |    |    +--- androidx.core:core:1.0.0 -> 1.9.0 (*)
                |     |    |    +--- androidx.lifecycle:lifecycle-livedata:2.0.0
                |     |    |    |    +--- androidx.arch.core:core-runtime:2.0.0
                |     |    |    |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |    |    |    |    \--- androidx.arch.core:core-common:2.0.0 -> 2.1.0 (*)
                |     |    |    |    +--- androidx.lifecycle:lifecycle-livedata-core:2.0.0 -> 2.5.1 (*)
                |     |    |    |    \--- androidx.arch.core:core-common:2.0.0 -> 2.1.0 (*)
                |     |    |    \--- androidx.lifecycle:lifecycle-viewmodel:2.0.0 -> 2.5.1 (*)
                |     |    +--- androidx.activity:activity:1.2.4 -> 1.6.0 (*)
                |     |    +--- androidx.lifecycle:lifecycle-livedata-core:2.3.1 -> 2.5.1 (*)
                |     |    +--- androidx.lifecycle:lifecycle-viewmodel:2.3.1 -> 2.5.1 (*)
                |     |    +--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.3.1 -> 2.5.1 (*)
                |     |    +--- androidx.savedstate:savedstate:1.1.0 -> 1.2.0 (*)
                |     |    \--- androidx.annotation:annotation-experimental:1.0.0 -> 1.3.0 (*)
                |     \--- androidx.savedstate:savedstate:1.2.0 (*)
            """.trimMargin()
        )
        val appcompat = root.children.single() as MavenDependencyNode
        assertEquals("androidx.appcompat:appcompat:1.6.1", appcompat.toString())
        assertEquals(listOf("aar"), appcompat.dependency.files.keys.sortedBy { it })
    }

    @Test
    fun `com_google_guava guava 33_0_0-android`(testInfo: TestInfo) {
        doTest(
            testInfo,
            platform = "android",
            expected = """root
                |\--- com.google.guava:guava:33.0.0-android
                |     +--- com.google.guava:failureaccess:1.0.2
                |     +--- com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava
                |     +--- com.google.code.findbugs:jsr305:3.0.2
                |     +--- org.checkerframework:checker-qual:3.41.0
                |     +--- com.google.errorprone:error_prone_annotations:2.23.0
                |     \--- com.google.j2objc:j2objc-annotations:2.8
            """.trimMargin()
        )
    }

    @Test
    fun `org_jetbrains_packagesearch packagesearch-plugin 1_0_0-SNAPSHOT`(testInfo: TestInfo) {
        doTest(
            testInfo,
            repositories = REDIRECTOR_MAVEN2 + "https://packages.jetbrains.team/maven/p/kpm/public",
            expected = """root
                |\--- org.jetbrains.packagesearch:packagesearch-plugin:1.0.0-SNAPSHOT
                |     \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0
                |          +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.0
                |          |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.0
                |          |    \--- org.jetbrains:annotations:13.0
                |          \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.0
                |               \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.0 (*)
            """.trimMargin()
        )
    }

    /**
     * TODO: org.jetbrains.kotlin:kotlin-test-junit:1.9.20 (*) is missing from org.jetbrains.kotlin:kotlin-test:1.9.20
     */
    @Test
    fun `kotlin test with junit`() {
        val root = Resolver(
            listOf(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.20",
                "org.jetbrains.kotlin:kotlin-test-junit:1.9.20",
                "org.jetbrains.kotlin:kotlin-test:1.9.20",
                "junit:junit:4.12",
            ).toRootNode(Context { repositories = REDIRECTOR_MAVEN2 })
        ).buildGraph(ResolutionLevel.NETWORK).root
        assertEquals(
            """root
            |+--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20
            ||    \--- org.jetbrains:annotations:13.0
            |+--- org.jetbrains.kotlin:kotlin-test-junit:1.9.20
            ||    +--- org.jetbrains.kotlin:kotlin-test:1.9.20
            ||    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20 (*)
            ||    \--- junit:junit:4.13.2
            ||         \--- org.hamcrest:hamcrest-core:1.3
            |+--- org.jetbrains.kotlin:kotlin-test:1.9.20 (*)
            |\--- junit:junit:4.12 -> 4.13.2 (*)
        """.trimMargin(),
            root
        )
    }

    @Test
    fun `kotlin test with junit5`() {
        val root = Resolver(
            listOf(
                "org.jetbrains.kotlin:kotlin-test-junit5:1.9.20",
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.20",
                "org.jetbrains.kotlin:kotlin-stdlib-common:1.9.20",
            ).toRootNode(Context { repositories = REDIRECTOR_MAVEN2 })
        ).buildGraph(ResolutionLevel.NETWORK).root
        assertEquals(
            """root
            |+--- org.jetbrains.kotlin:kotlin-test-junit5:1.9.20
            ||    +--- org.jetbrains.kotlin:kotlin-test:1.9.20
            ||    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20
            ||    |         \--- org.jetbrains:annotations:13.0
            ||    \--- org.junit.jupiter:junit-jupiter-api:5.6.3
            ||         +--- org.junit:junit-bom:5.6.3
            ||         +--- org.apiguardian:apiguardian-api:1.1.0
            ||         +--- org.opentest4j:opentest4j:1.2.0
            ||         \--- org.junit.platform:junit-platform-commons:1.6.3
            ||              +--- org.junit:junit-bom:5.6.3
            ||              \--- org.apiguardian:apiguardian-api:1.1.0
            |+--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20 (*)
            |\--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.20
        """.trimMargin(),
            root
        )
    }

    /**
     * TODO: org.jetbrains.kotlin:kotlin-stdlib-common:1.7.0 has to be upgraded
     */
    @Test
    fun `datetime and kotlin test with junit`() {
        val root = Resolver(
            listOf(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.20",
                "org.jetbrains.kotlinx:kotlinx-datetime:0.4.0",
                "org.jetbrains.kotlin:kotlin-test:1.9.0",
                "org.jetbrains.kotlin:kotlin-test-junit:1.9.20",
                "junit:junit:4.12",
            ).toRootNode(Context { repositories = REDIRECTOR_MAVEN2 })
        ).buildGraph(ResolutionLevel.NETWORK).root
        assertEquals(
            """root
            |+--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20
            ||    \--- org.jetbrains:annotations:13.0
            |+--- org.jetbrains.kotlinx:kotlinx-datetime:0.4.0
            ||    \--- org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0
            ||         +--- org.jetbrains.kotlin:kotlin-stdlib:1.7.0 -> 1.9.20 (*)
            ||         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.0
            |+--- org.jetbrains.kotlin:kotlin-test:1.9.0 -> 1.9.20
            ||    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20 (*)
            |+--- org.jetbrains.kotlin:kotlin-test-junit:1.9.20
            ||    +--- org.jetbrains.kotlin:kotlin-test:1.9.20 (*)
            ||    \--- junit:junit:4.13.2
            ||         \--- org.hamcrest:hamcrest-core:1.3
            |\--- junit:junit:4.12 -> 4.13.2 (*)
        """.trimMargin(),
            root
        )
    }

    @Test
    fun `jackson and guava`() {
        val root = Resolver(
            listOf(
                "org.antlr:antlr4-runtime:4.7.1",
                "org.abego.treelayout:org.abego.treelayout.core:1.0.3",
                "com.fasterxml.jackson.core:jackson-core:2.9.9",
                "com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider:2.9.9",
                "org.apache.commons:commons-lang3:3.9",
                "commons-io:commons-io:2.6",
                "org.reflections:reflections:0.9.8",
                "javax.inject:javax.inject:1",
                "net.openhft:compiler:2.3.4",
            ).toRootNode(Context { repositories = REDIRECTOR_MAVEN2 })
        ).buildGraph(ResolutionLevel.NETWORK).root
        assertEquals(
            """root
            |+--- org.antlr:antlr4-runtime:4.7.1
            |+--- org.abego.treelayout:org.abego.treelayout.core:1.0.3
            |+--- com.fasterxml.jackson.core:jackson-core:2.9.9
            |+--- com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider:2.9.9
            ||    +--- com.fasterxml.jackson.jaxrs:jackson-jaxrs-base:2.9.9
            ||    |    +--- com.fasterxml.jackson.core:jackson-core:2.9.9
            ||    |    \--- com.fasterxml.jackson.core:jackson-databind:2.9.9
            ||    |         +--- com.fasterxml.jackson.core:jackson-annotations:2.9.0
            ||    |         \--- com.fasterxml.jackson.core:jackson-core:2.9.9
            ||    \--- com.fasterxml.jackson.module:jackson-module-jaxb-annotations:2.9.9
            ||         +--- com.fasterxml.jackson.core:jackson-annotations:2.9.0
            ||         +--- com.fasterxml.jackson.core:jackson-core:2.9.9
            ||         \--- com.fasterxml.jackson.core:jackson-databind:2.9.9 (*)
            |+--- org.apache.commons:commons-lang3:3.9
            |+--- commons-io:commons-io:2.6
            |+--- org.reflections:reflections:0.9.8
            ||    +--- com.google.guava:guava:11.0.2
            ||    |    \--- com.google.code.findbugs:jsr305:1.3.9
            ||    +--- javassist:javassist:3.12.1.GA
            ||    \--- dom4j:dom4j:1.6.1
            ||         \--- xml-apis:xml-apis:1.0.b2
            |+--- javax.inject:javax.inject:1
            |\--- net.openhft:compiler:2.3.4
            |     +--- org.slf4j:slf4j-api:1.7.25
            |     \--- com.intellij:annotations:12.0
        """.trimMargin(),
            root
        )
    }

    private fun doTest(
        testInfo: TestInfo,
        dependency: String = testInfo.nameToDependency(),
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: String = "jvm",
        repositories: List<String> = REDIRECTOR_MAVEN2,
        verifyMessages: Boolean = true,
        @Language("text") expected: String
    ): DependencyNode {
        val root = Resolver(dependency.toRootNode(Context {
            this.scope = scope
            this.platform = platform
            this.repositories = repositories
        })).buildGraph(ResolutionLevel.NETWORK).root
        root.verifyGraphConnectivity()
        if (verifyMessages) {
            root.asSequence().forEach {
                assertTrue(
                    it.messages.none { "Downloaded from" !in it.text },
                    "There must be no messages for $it: ${it.messages}"
                )
            }
        }
        assertEquals(expected, root)
        return root
    }

    private fun DependencyNode.verifyGraphConnectivity() {
        val queue = LinkedList(listOf(this))
        while (queue.isNotEmpty()) {
            val node = queue.remove()
            node.children.forEach { assertEquals(node, it.parent, "Parents don't match") }
            queue += node.children
        }
    }

    private fun assertEquals(@Language("text") expected: String, root: DependencyNode) =
        assertEquals(expected, root.prettyPrint().trimEnd())

    companion object {
        private val REDIRECTOR_MAVEN2 = listOf("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
    }
}

private fun String.toRootNode(context: Context) = ModuleDependencyNode(context, "root", listOf(toMavenNode(context)))

private fun List<String>.toRootNode(context: Context) =
    ModuleDependencyNode(context, "root", map { it.toMavenNode(context) })

private fun String.toMavenNode(context: Context): MavenDependencyNode {
    val (group, module, version) = split(":")
    return MavenDependencyNode(context, group, module, version)
}
