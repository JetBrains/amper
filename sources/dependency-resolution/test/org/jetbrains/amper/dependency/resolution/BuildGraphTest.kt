/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.amper.test.TestUtil
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
        val root = doTest(
            testInfo,
            expected = """root
                |\--- org.jetbrains.kotlin:kotlin-test:1.9.10
                |     \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.10
                |          +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.10
                |          \--- org.jetbrains:annotations:13.0
            """.trimMargin()
        )
        assertFiles(
            """annotations-13.0-sources.jar
                |annotations-13.0.jar
                |kotlin-stdlib-1.9.10-sources.jar
                |kotlin-stdlib-1.9.10.jar
                |kotlin-stdlib-common-1.9.10-sources.jar
                |kotlin-stdlib-common-1.9.10.jar
                |kotlin-test-1.9.10.jar""".trimMargin(),
            root
        )
    }

    @Test
    fun `org_jetbrains_kotlinx atomicfu 0_23_2`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            platform = ResolutionPlatform.JVM,
            expected = """root
                |\--- org.jetbrains.kotlinx:atomicfu:0.23.2
                |     \--- org.jetbrains.kotlinx:atomicfu-jvm:0.23.2
            """.trimMargin()
        )
        assertFiles(
            """atomicfu-jvm-0.23.2.jar""".trimMargin(),
            root
        )

        root.distinctBfsSequence().forEach {
            assertTrue(
                it.messages.none { it.severity == Severity.ERROR },
                "There should be no messages for $it: ${it.messages.filter{ it.severity == Severity.ERROR} }"
            )
        }

    }

    @Test
    fun `org_jetbrains_kotlin kotlin-test 1_9_20`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            scope = ResolutionScope.RUNTIME,
            expected = """root
                |\--- org.jetbrains.kotlin:kotlin-test:1.9.20
                |     \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20
                |          \--- org.jetbrains:annotations:13.0
            """.trimMargin()
        )
        assertFiles(
            """annotations-13.0-sources.jar
                |annotations-13.0.jar
                |kotlin-stdlib-1.9.20-sources.jar
                |kotlin-stdlib-1.9.20.jar
                |kotlin-test-1.9.20.jar""".trimMargin(),
            root
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
                |     \--- org.jetbrains.skiko:skiko-awt:0.7.85
                |          \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20
                |               +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20
                |               |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.20
                |               |    \--- org.jetbrains:annotations:13.0
                |               \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.20
                |                    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20 (*)
            """.trimMargin()
        )
        root.distinctBfsSequence().forEach {
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
        root.distinctBfsSequence().forEach {
            assertTrue(
                it.messages.none { "Downloaded from" !in it.text && "More than a single variant provided" !in it.text },
                "There should be no messages for $it: ${it.messages}"
            )
        }
        root.distinctBfsSequence()
            .mapNotNull { it as? MavenDependencyNode }
            .flatMap { it.dependency.files }
            .mapNotNull { runBlocking { it.getPath() } }
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
            platform = ResolutionPlatform.ANDROID,
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
                |     |    |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10
                |     |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.10
                |     |    |    |    |    |    |    \--- org.jetbrains:annotations:13.0
                |     |    |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4
                |     |    |    |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4
                |     |    |    |    |    |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4
                |     |    |    |    |    |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
                |     |    |    |    |    |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21
                |     |    |    |    |    |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 1.8.10 (*)
                |     |    |    |    |    |         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21
                |     |    |    |    |    |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 1.8.10 (*)
                |     |    |    |    |    |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.21 -> 1.8.10
                |     |    |    |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
                |     |    |    |    |    |         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21 (*)
                |     |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
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
                |     |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.6.1 (*)
                |     |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 (*)
                |     |    |    |    +--- androidx.savedstate:savedstate:1.2.1
                |     |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4 (*)
                |     |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
                |     |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    +--- androidx.core:core-ktx:1.1.0 -> 1.2.0 (*)
                |     |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.6.1
                |     |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.2.0
                |     |    |    +--- androidx.lifecycle:lifecycle-runtime:2.6.1 (*)
                |     |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4 (*)
                |     |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1
                |     |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 (*)
                |     |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4 (*)
                |     |    +--- androidx.savedstate:savedstate-ktx:1.2.1
                |     |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
                |     |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     +--- androidx.compose.runtime:runtime:1.0.1
                |     |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.0 -> 1.6.4 (*)
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
            platform = ResolutionPlatform.ANDROID,
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
        assertEquals(listOf("aar"), appcompat.dependency.files.map { it.extension }.sortedBy { it })
        assertFiles("""
            activity-1.6.0.aar
            annotation-1.3.0.jar
            annotation-experimental-1.3.0.aar
            annotations-13.0-sources.jar
            annotations-13.0.jar
            appcompat-1.6.1.aar
            appcompat-resources-1.6.1.aar
            collection-1.1.0-sources.jar
            collection-1.1.0.jar
            core-1.9.0.aar
            core-common-2.1.0-sources.jar
            core-common-2.1.0.jar
            core-ktx-1.2.0.aar
            core-runtime-2.0.0.aar
            cursoradapter-1.0.0.aar
            customview-1.0.0.aar
            drawerlayout-1.0.0.aar
            fragment-1.3.6.aar
            interpolator-1.0.0.aar
            kotlin-stdlib-1.7.10-sources.jar
            kotlin-stdlib-1.7.10.jar
            kotlin-stdlib-common-1.7.10-sources.jar
            kotlin-stdlib-common-1.7.10.jar
            kotlin-stdlib-jdk7-1.6.0-sources.jar
            kotlin-stdlib-jdk7-1.6.0.jar
            kotlin-stdlib-jdk8-1.6.0-sources.jar
            kotlin-stdlib-jdk8-1.6.0.jar
            kotlinx-coroutines-android-1.6.1.jar
            kotlinx-coroutines-core-jvm-1.6.1.jar
            lifecycle-common-2.5.1.jar
            lifecycle-livedata-2.0.0.aar
            lifecycle-livedata-core-2.5.1.aar
            lifecycle-runtime-2.5.1.aar
            lifecycle-viewmodel-2.5.1.aar
            lifecycle-viewmodel-savedstate-2.5.1.aar
            loader-1.0.0.aar
            savedstate-1.2.0.aar
            vectordrawable-1.1.0.aar
            vectordrawable-animated-1.1.0.aar
            versionedparcelable-1.1.1.aar
            viewpager-1.0.0.aar
        """.trimIndent(), root)
    }

    @Test
    fun `com_google_guava guava 33_0_0-android`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            platform = ResolutionPlatform.ANDROID,
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
        assertFiles(
            """checker-qual-3.41.0.jar
                |error_prone_annotations-2.23.0-sources.jar
                |error_prone_annotations-2.23.0.jar
                |failureaccess-1.0.2-sources.jar
                |failureaccess-1.0.2.jar
                |guava-33.0.0-android.jar
                |j2objc-annotations-2.8-sources.jar
                |j2objc-annotations-2.8.jar
                |jsr305-3.0.2-sources.jar
                |jsr305-3.0.2.jar
                |listenablefuture-9999.0-empty-to-avoid-conflict-with-guava-sources.jar
                |listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar
            """.trimMargin(),
            root
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

    @Test
    fun `org_jetbrains_kotlinx kotlinx-datetime 0_5_0`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            platform = ResolutionPlatform.MACOS_ARM64,
            expected = """root
                |\--- org.jetbrains.kotlinx:kotlinx-datetime:0.5.0
                |     \--- org.jetbrains.kotlinx:kotlinx-datetime-macosarm64:0.5.0
                |          +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
                |          |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-macosarm64:1.6.2
                |          |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21
                |          |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21
                |          \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21
            """.trimMargin()
        )
        assertFiles(
            """kotlinx-datetime-macosarm64-0.5.0.klib
                |kotlinx-serialization-core-macosarm64-1.6.2.klib""".trimMargin(),
            root
        )
    }

    /**
     * TODO: org.jetbrains.kotlin:kotlin-test-junit:1.9.20 (*) is missing from org.jetbrains.kotlin:kotlin-test:1.9.20
     */
    @Test
    fun `kotlin test with junit`() {
        context().use { context ->
            val rootNode = listOf(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.20",
                "org.jetbrains.kotlin:kotlin-test-junit:1.9.20",
                "org.jetbrains.kotlin:kotlin-test:1.9.20",
                "junit:junit:4.12",
            ).toRootNode(context)
            val resolver = Resolver()
            val root = rootNode
            runBlocking { resolver.buildGraph(root, ResolutionLevel.NETWORK) }
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
    }

    @Test
    fun `kotlin test with junit5`() {
        context().use { context ->
            val root = listOf(
                "org.jetbrains.kotlin:kotlin-test-junit5:1.9.20",
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.20",
                "org.jetbrains.kotlin:kotlin-stdlib-common:1.9.20",
            ).toRootNode(context)
            val resolver = Resolver()
            runBlocking { resolver.buildGraph(root, ResolutionLevel.NETWORK) }
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
    }

    /**
     * TODO: org.jetbrains.kotlin:kotlin-stdlib-common:1.7.0 has to be upgraded
     */
    @Test
    fun `datetime and kotlin test with junit`() {
        context().use { context ->
            val root = listOf(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.20",
                "org.jetbrains.kotlinx:kotlinx-datetime:0.4.0",
                "org.jetbrains.kotlin:kotlin-test:1.9.0",
                "org.jetbrains.kotlin:kotlin-test-junit:1.9.20",
                "junit:junit:4.12",
            ).toRootNode(context)
            val resolver = Resolver()
            runBlocking { resolver.buildGraph(root, ResolutionLevel.NETWORK) }
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
    }

    @Test
    fun `jackson and guava`() {
        context().use { context ->
            val root = listOf(
                "org.antlr:antlr4-runtime:4.7.1",
                "org.abego.treelayout:org.abego.treelayout.core:1.0.3",
                "com.fasterxml.jackson.core:jackson-core:2.9.9",
                "com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider:2.9.9",
                "org.apache.commons:commons-lang3:3.9",
                "commons-io:commons-io:2.6",
                "org.reflections:reflections:0.9.8",
                "javax.inject:javax.inject:1",
                "net.openhft:compiler:2.3.4",
            ).toRootNode(context)
            val resolver = Resolver()
            runBlocking { resolver.buildGraph(root, ResolutionLevel.NETWORK) }
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
    }

    @Test
    fun `huge dependency graph with reused subgraphs`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            dependency = "org.jetbrains.compose.desktop:desktop-jvm-windows-x64:1.5.10",
            expected = """
                root
                \--- org.jetbrains.compose.desktop:desktop-jvm-windows-x64:1.5.10
                     +--- org.jetbrains.compose.desktop:desktop:1.5.10
                     |    +--- org.jetbrains.compose.desktop:desktop-jvm:1.5.10
                     |    |    +--- org.jetbrains.compose.foundation:foundation:1.5.10
                     |    |    |    +--- org.jetbrains.compose.foundation:foundation-desktop:1.5.10
                     |    |    |    |    +--- org.jetbrains.compose.animation:animation:1.5.10
                     |    |    |    |    |    +--- org.jetbrains.compose.animation:animation-desktop:1.5.10
                     |    |    |    |    |    |    +--- org.jetbrains.compose.animation:animation-core:1.5.10
                     |    |    |    |    |    |    |    +--- org.jetbrains.compose.animation:animation-core-desktop:1.5.10
                     |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10
                     |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime-desktop:1.5.10
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21
                     |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains:annotations:13.0 -> 23.0.0
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.17.0
                     |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.kotlinx:atomicfu-jvm:0.17.0
                     |    |    |    |    |    |    |    |    |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.6.0 -> 1.8.21 (*)
                     |    |    |    |    |    |    |    |    |    |    |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.0 -> 1.8.21
                     |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 -> 1.7.3
                     |    |    |    |    |    |    |    |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3
                     |    |    |    |    |    |    |    |    |    |         |    +--- org.jetbrains:annotations:23.0.0
                     |    |    |    |    |    |    |    |    |    |         |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.3
                     |    |    |    |    |    |    |    |    |    |         |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.20 -> 1.8.21
                     |    |    |    |    |    |    |    |    |    |         |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 -> 1.8.21
                     |    |    |    |    |    |    |    |    |    |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    |    |    |    |    |    |    |    |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.21
                     |    |    |    |    |    |    |    |    |    |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    |    |    |    |    |    |    |    |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3 (*)
                     |    |    |    |    |    |    |    |    |    \--- org.jetbrains.compose.runtime:runtime-desktop:1.5.10 (*)
                     |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui:1.5.10
                     |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-desktop:1.5.10
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime-saveable:1.5.10
                     |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime-saveable-desktop:1.5.10
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.compose.runtime:runtime-saveable-desktop:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.5.10
                     |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry-desktop:1.5.10
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.5.10
                     |    |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util-desktop:1.5.10
                     |    |    |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.compose.ui:ui-util-desktop:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.compose.ui:ui-geometry-desktop:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-graphics:1.5.10
                     |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-graphics-desktop:1.5.10
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.5.10
                     |    |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-unit-desktop:1.5.10
                     |    |    |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.compose.ui:ui-unit-desktop:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.21 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.skiko:skiko:0.7.85
                     |    |    |    |    |    |    |    |    |    |    |    |         \--- org.jetbrains.skiko:skiko-awt:0.7.85
                     |    |    |    |    |    |    |    |    |    |    |    |              +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 -> 1.8.21 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20 -> 1.8.21 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |              +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3 (*)
                     |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.compose.ui:ui-graphics-desktop:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-text:1.5.10
                     |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-text-desktop:1.5.10
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime-saveable:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-graphics:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.17.0 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 -> 1.7.3 (*)
                     |    |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.skiko:skiko:0.7.85 (*)
                     |    |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.compose.ui:ui-text-desktop:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.21 (*)
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlinx:atomicfu:0.17.0 (*)
                     |    |    |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 -> 1.7.3 (*)
                     |    |    |    |    |    |    |    |    |    |    \--- org.jetbrains.skiko:skiko:0.7.85 (*)
                     |    |    |    |    |    |    |    |    |    \--- org.jetbrains.compose.ui:ui-desktop:1.5.10 (*)
                     |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-unit:1.5.10 (*)
                     |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 -> 1.7.3 (*)
                     |    |    |    |    |    |    |    \--- org.jetbrains.compose.animation:animation-core-desktop:1.5.10 (*)
                     |    |    |    |    |    |    +--- org.jetbrains.compose.foundation:foundation-layout:1.5.10
                     |    |    |    |    |    |    |    +--- org.jetbrains.compose.foundation:foundation-layout-desktop:1.5.10
                     |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                     |    |    |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |    |    |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    |    |    |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    |    |    \--- org.jetbrains.compose.foundation:foundation-layout-desktop:1.5.10 (*)
                     |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                     |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-geometry:1.5.10 (*)
                     |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    \--- org.jetbrains.compose.animation:animation-desktop:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.foundation:foundation-layout:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.ui:ui-text:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    \--- org.jetbrains.skiko:skiko:0.7.85 (*)
                     |    |    |    \--- org.jetbrains.compose.foundation:foundation-desktop:1.5.10 (*)
                     |    |    +--- org.jetbrains.compose.material:material:1.5.10
                     |    |    |    +--- org.jetbrains.compose.material:material-desktop:1.5.10
                     |    |    |    |    +--- org.jetbrains.compose.animation:animation:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.animation:animation-core:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.foundation:foundation:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.foundation:foundation-layout:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.material:material-icons-core:1.5.10
                     |    |    |    |    |    +--- org.jetbrains.compose.material:material-icons-core-desktop:1.5.10
                     |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                     |    |    |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    \--- org.jetbrains.compose.material:material-icons-core-desktop:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.material:material-ripple:1.5.10
                     |    |    |    |    |    +--- org.jetbrains.compose.material:material-ripple-desktop:1.5.10
                     |    |    |    |    |    |    +--- org.jetbrains.compose.animation:animation:1.5.10 (*)
                     |    |    |    |    |    |    +--- org.jetbrains.compose.foundation:foundation:1.5.10 (*)
                     |    |    |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |    |    |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |    |    |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    |    |    \--- org.jetbrains.compose.material:material-ripple-desktop:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.ui:ui-text:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    \--- org.jetbrains.compose.material:material-desktop:1.5.10 (*)
                     |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |    |    +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                     |    |    +--- org.jetbrains.compose.ui:ui-tooling-preview:1.5.10
                     |    |    |    +--- org.jetbrains.compose.ui:ui-tooling-preview-desktop:1.5.10
                     |    |    |    |    +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    |    \--- org.jetbrains.compose.ui:ui-tooling-preview-desktop:1.5.10 (*)
                     |    |    +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.21 (*)
                     |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 -> 1.7.3 (*)
                     |    \--- org.jetbrains.compose.desktop:desktop-jvm:1.5.10 (*)
                     \--- org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.7.85
                          \--- org.jetbrains.skiko:skiko-awt:0.7.85 (*)
            """.trimIndent(),
            scope = ResolutionScope.RUNTIME,
            verifyMessages = false,
        )

        assertFiles("""
            animation-core-desktop-1.5.10-sources.jar
            animation-core-desktop-1.5.10.jar
            animation-desktop-1.5.10-sources.jar
            animation-desktop-1.5.10.jar
            annotations-23.0.0-sources.jar
            annotations-23.0.0.jar
            atomicfu-jvm-0.17.0.jar
            desktop-jvm-1.5.10-sources.jar
            desktop-jvm-1.5.10.jar
            foundation-desktop-1.5.10-sources.jar
            foundation-desktop-1.5.10.jar
            foundation-layout-desktop-1.5.10-sources.jar
            foundation-layout-desktop-1.5.10.jar
            kotlin-stdlib-1.8.21-sources.jar
            kotlin-stdlib-1.8.21.jar
            kotlin-stdlib-common-1.8.21-sources.jar
            kotlin-stdlib-common-1.8.21.jar
            kotlin-stdlib-jdk7-1.8.21-sources.jar
            kotlin-stdlib-jdk7-1.8.21.jar
            kotlin-stdlib-jdk8-1.8.21-sources.jar
            kotlin-stdlib-jdk8-1.8.21.jar
            kotlinx-coroutines-core-jvm-1.7.3-sources.jar
            kotlinx-coroutines-core-jvm-1.7.3.jar
            material-desktop-1.5.10-sources.jar
            material-desktop-1.5.10.jar
            material-icons-core-desktop-1.5.10-sources.jar
            material-icons-core-desktop-1.5.10.jar
            material-ripple-desktop-1.5.10-sources.jar
            material-ripple-desktop-1.5.10.jar
            runtime-desktop-1.5.10-sources.jar
            runtime-desktop-1.5.10.jar
            runtime-saveable-desktop-1.5.10-sources.jar
            runtime-saveable-desktop-1.5.10.jar
            skiko-awt-0.7.85-sources.jar
            skiko-awt-0.7.85.jar
            skiko-awt-runtime-windows-x64-0.7.85-sources.jar
            skiko-awt-runtime-windows-x64-0.7.85.jar
            ui-desktop-1.5.10-sources.jar
            ui-desktop-1.5.10.jar
            ui-geometry-desktop-1.5.10-sources.jar
            ui-geometry-desktop-1.5.10.jar
            ui-graphics-desktop-1.5.10-sources.jar
            ui-graphics-desktop-1.5.10.jar
            ui-text-desktop-1.5.10-sources.jar
            ui-text-desktop-1.5.10.jar
            ui-tooling-preview-desktop-1.5.10-sources.jar
            ui-tooling-preview-desktop-1.5.10.jar
            ui-unit-desktop-1.5.10-sources.jar
            ui-unit-desktop-1.5.10.jar
            ui-util-desktop-1.5.10-sources.jar
            ui-util-desktop-1.5.10.jar
        """.trimIndent(), root)
    }

    private fun doTest(
        testInfo: TestInfo,
        dependency: String = testInfo.nameToDependency(),
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: ResolutionPlatform = ResolutionPlatform.JVM,
        repositories: List<String> = REDIRECTOR_MAVEN2,
        verifyMessages: Boolean = true,
        @Language("text") expected: String
    ): DependencyNode {
        context(scope, platform, repositories).use { context ->
            val root = dependency.toRootNode(context)
            val resolver = Resolver()
            runBlocking { resolver.buildGraph(root, ResolutionLevel.NETWORK) }
            root.verifyGraphConnectivity()
            if (verifyMessages) {
                root.distinctBfsSequence().forEach {
                    val messages = it.messages.filter { "Downloaded from" !in it.text }
                    assertTrue(messages.isEmpty(), "There must be no messages for $it: $messages")
                }
            }
            assertEquals(expected, root)
            return root
        }
    }

    private fun context(
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: ResolutionPlatform = ResolutionPlatform.JVM,
        repositories: List<String> = REDIRECTOR_MAVEN2,
    ) = Context {
        this.scope = scope
        this.platforms = setOf(platform)
        this.repositories = repositories
        this.cache = {
            amperCache = TestUtil.userCacheRoot.resolve(".amper")
            localRepositories = listOf(MavenLocalRepository(TestUtil.userCacheRoot.resolve(".m2.cache")))
        }
    }

    private fun DependencyNode.verifyGraphConnectivity() {
        val queue = LinkedList(listOf(this))
        val verified = mutableSetOf<DependencyNode>()
        while (queue.isNotEmpty()) {
            val node = queue.remove()
            node.children.forEach { assertTrue(node in it.parents, "Parents don't match") }
            verified.add(node)
            queue += node.children.filter { it !in verified }
        }
    }

    private fun assertEquals(@Language("text") expected: String, root: DependencyNode) =
        assertEquals(expected, root.prettyPrint().trimEnd())

    private fun assertFiles(files: String, root: DependencyNode) {
        root.distinctBfsSequence()
            .mapNotNull { it as? MavenDependencyNode }
            .flatMap { it.dependency.files }
            .mapNotNull { runBlocking { it.getPath()?.name } }
            .sorted()
            .toSet()
            .let { assertEquals(files, it.joinToString("\n")) }
    }

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
