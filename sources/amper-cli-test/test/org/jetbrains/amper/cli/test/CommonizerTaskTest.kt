/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.core.UsedVersions.kotlinVersion
import org.jetbrains.amper.test.MacOnly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Path
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class CommonizerTaskTest: AmperCliTestBase() {

    @Test
    @MacOnly
    fun `commonize ios`() = runSlowTest {
        val commonizedRootDir = runCommonizerTask(projectName = "kmp-mobile")

        val expectedPlatformSets = listOf(
            listOf("ios_arm64", "ios_simulator_arm64", "ios_x64")
        )
        assertCommonizedPlatformSets(expectedPlatformSets, commonizedRootDir)
    }

    @Test
    fun `commonize one windows and one linux`() = runSlowTest {
        val commonizedRootDir = runCommonizerTask(projectName = "win-and-linuxX64")

        val expectedPlatformSets = listOf(
            listOf("linux_x64", "mingw_x64")
        )
        assertCommonizedPlatformSets(expectedPlatformSets, commonizedRootDir)
    }

    @Test
    @MacOnly
    fun `commonize ios and two linuxes`() = runSlowTest {
        val commonizedRootDir = runCommonizerTask(projectName = "ios-and-two-linux")

        val expectedPlatformSets = listOf(
            listOf("ios_arm64", "ios_simulator_arm64", "ios_x64"), // for the 'ios' fragment
            listOf("linux_arm64", "linux_x64"),  // for the 'linux' fragment
            listOf("ios_arm64", "ios_simulator_arm64", "ios_x64", "linux_arm64", "linux_x64") // for the 'native' fragment
        )
        assertCommonizedPlatformSets(expectedPlatformSets, commonizedRootDir)
    }

    @Test
    fun `commonize one windows and two linuxes`() = runSlowTest {
        val commonizedRootDir = runCommonizerTask(projectName = "win-and-two-linux")

        val expectedPlatformSets = listOf(
            listOf("linux_arm64", "linux_x64"),  // for the 'linux' fragment
            listOf("linux_arm64", "linux_x64", "mingw_x64") // for the 'native' fragment
        )
        assertCommonizedPlatformSets(expectedPlatformSets, commonizedRootDir)
    }

    private suspend fun runCommonizerTask(projectName: String): Path {
        val konanDataDir = tempRoot / UUID.randomUUID().toString()
        konanDataDir.createDirectories()

        val runResult = runCli(
            projectRoot = testProject("commonizer/$projectName"),
            "task", "commonizeNativeDistribution",
            environment = mapOf("KONAN_DATA_DIR" to konanDataDir.toString())
        )

        assertEquals(runResult.exitCode, 0, "The commonizer task failed with exit code ${runResult.exitCode}")
        val commonizedRootDir = konanDataDir / "klib" / "commonized" / kotlinVersion
        assertTrue(commonizedRootDir.exists(), "$commonizedRootDir directory does not exist")
        return commonizedRootDir
    }

    private fun assertCommonizedPlatformSets(expectedPlatformSets: List<List<String>>, commonizedRootDir: Path) {
        for (expectedPlatformSet in expectedPlatformSets) {
            val folder = "(${expectedPlatformSet.joinToString()})"
            assertTrue((commonizedRootDir / folder).exists(),
                "$folder was not generated in $commonizedRootDir. " +
                "All files inside:\n ${commonizedRootDir.listDirectoryEntries().joinToString(separator = "\n") { it.name }}"
            )
        }
    }
}
