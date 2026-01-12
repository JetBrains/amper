/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.getTaskOutputPath
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.frontend.Platform
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.absolute
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourcesJarTest : AmperCliTestBase() {

    companion object {
        @JvmStatic
        fun platforms(): List<Platform> = listOf(
            Platform.JVM,
            Platform.JS,
            Platform.WASM_JS,
            Platform.WASM_WASI,
            Platform.LINUX_X64,
            Platform.LINUX_ARM64,
            Platform.MACOS_ARM64,
            Platform.MINGW_X64,
        )
    }

    @ParameterizedTest
    @MethodSource("platforms")
    fun `simple multiplatform cli sources jars`(platform: Platform) = runSlowTest {
        val projectRoot = testProject("simple-multiplatform-cli")
        val taskName = ":shared:sourcesJar${platform.schemaValue.replaceFirstChar { it.titlecase() }}"
        val result = runCli(projectRoot = projectRoot, "task", taskName)

        assertJarFileEntries(
            jarPath = result.getTaskOutputPath(taskName) / "shared-${platform.schemaValue.lowercase()}-sources.jar",
            expectedEntries = buildList {
                add("META-INF/MANIFEST.MF")
                add("commonMain/")
                add("commonMain/World.kt")
                add("commonMain/program1.kt")
                add("commonMain/program2.kt")
                when (platform) {
                    Platform.JVM -> {
                        add("jvmMain/")
                        add("jvmMain/Jvm.java")
                        add("jvmMain/World.kt")
                    }
                    Platform.JS -> {
                        add("jsMain/")
                        add("jsMain/World.kt")
                    }
                    Platform.WASM_JS -> {
                        add("wasmJsMain/")
                        add("wasmJsMain/World.kt")
                    }
                    Platform.WASM_WASI -> {
                        add("wasmWasiMain/")
                        add("wasmWasiMain/World.kt")
                    }
                    Platform.LINUX_X64, Platform.LINUX_ARM64 -> {
                        add("linuxMain/")
                        add("linuxMain/World.kt")
                    }
                    Platform.MACOS_ARM64 -> {
                        add("macosMain/")
                        add("macosMain/World.kt")
                    }
                    Platform.MINGW_X64 -> {
                        add("mingwMain/")
                        add("mingwMain/World.kt")
                    }
                    else -> error("Unexpected platform: $platform")
                }
            }
        )
    }
}

private fun assertJarFileEntries(jarPath: Path, expectedEntries: List<String>) {
    assertTrue(jarPath.existsCaseSensitive(), "Jar file $jarPath doesn't exist")
    JarFile(jarPath.toFile()).use { jar ->
        assertEquals(expectedEntries, jar.entries().asSequence().map { it.name }.toList())
    }
}

private fun Path.existsCaseSensitive(): Boolean =
    // toRealPath() ensures the case matches what's on disk even on Windows
    exists() && absolute().normalize().pathString == toRealPath(LinkOption.NOFOLLOW_LINKS).pathString
