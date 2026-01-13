/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.schema.JvmDistribution
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.system.info.Arch
import org.jetbrains.amper.system.info.OsFamily
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.TempDirExtension
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

class JdkProviderTest {

    @RegisterExtension
    private val tempDirExtension = TempDirExtension()

    @Test
    fun provisionJdk_21() = runTest(timeout = 10.minutes) {
        assertValidJdk(JdkProvisioningCriteria(majorVersion = 21))
    }

    @Test
    fun provisionJdk_25() = runTest(timeout = 10.minutes) {
        assertValidJdk(JdkProvisioningCriteria(majorVersion = 25))
    }

    @ParameterizedTest
    @EnumSource
    fun provisionJdk_distributionsAreCorrectlyDetected(distribution: JvmDistribution) = runTest(timeout = 10.minutes) {
        assumeFalse(
            OsFamily.current == OsFamily.MacOs && Arch.current == Arch.Arm64
                    && distribution == JvmDistribution.AlibabaDragonwell,
            "there is no Dragonwell JDK for macOS ARM64",
        )

        // assertValidJdk already checks that the detected distribution in the provisioned JDK actually matches the
        // requested one (because it's the only one in the list in this test case).
        assertValidJdk(
            JdkProvisioningCriteria(
                // there is no Dragonwell JDK 25 yet
                majorVersion = if (distribution == JvmDistribution.AlibabaDragonwell) 21 else 25,
                distributions = listOf(distribution),
                acknowledgedLicenses = buildList {
                    if (distribution.requiresLicense) {
                        add(distribution)
                    }
                },
            )
        )
    }

    /**
     * Some distributions for macOS have the actual JDK nested in the archive under `/<someSingleRootDir>/Contents/Home`.
     *
     * Example: Corretto 21
     * ```
     * amazon-corretto-21.jdk
     * ╰─ Contents
     *    ├─ _CodeSignature
     *    ├─ Home
     *    │  ╰─ <jdk here>
     *    ├─ MacOS
     *    ╰─ info.plist
     * ```
     */
    @Test
    fun provisionJdk_nestedContentHome() = runTest(timeout = 10.minutes) {
        assertValidJdk(
            JdkProvisioningCriteria(
                majorVersion = 21,
                distributions = listOf(JvmDistribution.AmazonCorretto),
                operatingSystems = listOf(OsFamily.MacOs),
            )
        )
    }

    /**
     * Some distributions for macOS have the actual JDK nested in the archive under a series of directories that have
     * no siblings, and then `./Contents/Home`. This means `STRIP_ROOT` when extracting is not sufficient.
     * 
     * Example: Zulu 8
     * ```
     * zulu8.90.0.19-ca-jdk8.0.472-macosx_x64
     * ╰─ zulu-8.jdk
     *    ╰─ Contents
     *       ├─ _CodeSignature
     *       ├─ Home
     *       │  ╰─ <jdk here>
     *       ├─ MacOS
     *       ╰─ info.plist
     * ```
     */
    @Test
    fun provisionJdk_nestedSingleRoot_nestedContentHome() = runTest(timeout = 10.minutes) {
        assertValidJdk(
            // happens with Zulu 8, Microsoft 11, OpenLogic 11
            JdkProvisioningCriteria(
                majorVersion = 8,
                distributions = listOf(JvmDistribution.AzulZulu),
                operatingSystems = listOf(OsFamily.MacOs),
            )
        )
    }

    /**
     *  Some distributions for macOS have the actual JDK nested in the archive under a series of directories that have
     *  no sibling directories (but do have sibling files like READMEs), and then ./Contents/Home. This means STRIP_ROOT
     *  doesn't work and wouldn't be sufficient anyway.
     *
     *  Example: Zulu 21
     *
     *  ```
     *  zulu21.46.19-ca-jdk21.0.9-macosx_x64
     *  ├─ zulu-21.jdk
     *  │  ╰─ Contents
     *  │     ├─ _CodeSignature
     *  │     ├─ Home
     *  │     │  ╰─ <jdk here>
     *  │     ├─ MacOS
     *  │     ╰─ info.plist
     *  ╰─ readme.txt
     *  ```
     *
     *  Note: this archive actually contains many more sibling files to zulu-21.jdk originally, but these are symlinks
     *  and aren't present in the extracted directory (created by our extraction utilities).
     */
    @Test
    fun provisionJdk_nestedDirNextToTopLevelFiles_nestedContentHome() = runTest(timeout = 10.minutes) {
        assertValidJdk(
            // happens with Zulu 11/17/21
            JdkProvisioningCriteria(
                majorVersion = 21,
                distributions = listOf(JvmDistribution.AzulZulu),
                operatingSystems = listOf(OsFamily.MacOs),
            )
        )
    }

    // This tests archives with '.' in the zip entry names. This breaks the STRIP_ROOT option of the extractor
    // because it tries to ensure that all entries start with this leading '.' directory.
    @Test
    fun provisionJdk_entriesWithLeadingDotDir() = runTest(timeout = 10.minutes) {
        assertValidJdk(
            // happens with Semeru 8/11/17/21/25
            JdkProvisioningCriteria(
                majorVersion = 25,
                distributions = listOf(JvmDistribution.IbmSemeru),
                operatingSystems = listOf(OsFamily.MacOs),
            )
        )
    }

    @Test
    fun provisionJdk_failsWithNoResults() = runTest(timeout = 3.minutes) {
        val criteria = JdkProvisioningCriteria(
            majorVersion = 11,
            distributions = listOf(JvmDistribution.AmazonCorretto),
            operatingSystems = listOf(OsFamily.Windows), // no Corretto 11 for Windows ARM
            architectures = listOf(Arch.Arm64),
        )
        val jdkResult = createTestJdkProvider().provisionJdk(criteria)
        val failureMessage = """
            Could not find any JDK that match the criteria:
              - Major version: 11
              - Operating system(s): Windows
              - Architecture(s): arm64
              - Acceptable distribution(s): corretto
        """.trimIndent()
        assertEquals(JdkResult.Failure(failureMessage), jdkResult)
    }

    /**
     * Asserts a JDK can be provisioned, that it is a valid JDK, and that it matches the criteria used to provision it.
     */
    private suspend fun assertValidJdk(jdkProvisioningCriteria: JdkProvisioningCriteria) {
        val jdk = when (val jdkResult = createTestJdkProvider().provisionJdk(jdkProvisioningCriteria)) {
            is JdkResult.Failure -> fail("JDK should be resolved successfully but failed with: ${jdkResult.message}")
            is JdkResult.Success -> jdkResult.jdk
        }
        val expectedMajor = jdkProvisioningCriteria.majorVersion
        assert(jdk.version.startsWith(expectedMajor.toString()) || jdk.version.startsWith("1.${expectedMajor}")) {
            "the provisioned JDK version should match the major version criteria"
        }

        assertTrue(jdk.javaExecutable.exists(), "java executable should exist")
        assertTrue(jdk.javacExecutable.exists(), "javac executable should exist")
        if (jdkProvisioningCriteria.distributions != null) {
            assertContains(
                iterable = jdkProvisioningCriteria.distributions,
                element = jdk.distribution,
                message = "the provisioned distribution should be part of what we asked",
            )
        }
    }

    private fun createTestJdkProvider(): JdkProvider = JdkProvider(
        userCacheRoot = AmperUserCacheRoot(Dirs.userCacheRoot),
        incrementalCache = IncrementalCache(
            stateRoot = tempDirExtension.path / "jdk-provisioning-cache",
            codeVersion = "1", // doesn't matter, the cache is new in every test
        ),
    )
}
