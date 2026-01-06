/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.foojay.model.Architecture
import org.jetbrains.amper.foojay.model.OperatingSystem
import org.jetbrains.amper.frontend.schema.JvmDistribution
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.TempDirExtension
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
        // cannot use assumeFalse because our JUnit listener doesn't seem to support it properly
        if (OperatingSystem.current() != OperatingSystem.LINUX && distribution.isLinuxOnly()) {
            return@runTest
        }
        if (Architecture.current() == Architecture.AARCH64 && distribution == JvmDistribution.PerforceOpenLogic) {
            return@runTest // there are no OpenLogic ARM64 JDKs
        }
        if (OperatingSystem.current() == OperatingSystem.MACOS
            && Architecture.current() == Architecture.AARCH64
            && distribution == JvmDistribution.AlibabaDragonwell) {
            return@runTest // there are no Dragonwell JDKs for macOS ARM64
        }
        // We no longer get any ZIP package for SAP Machine JDK 21 on Windows
        // When https://github.com/foojayio/discoapi/issues/136 is fixed (if ever), we can remove this 'if'
        if (OperatingSystem.current() == OperatingSystem.WINDOWS && distribution == JvmDistribution.SapMachine) {
            return@runTest
        }
        // assertValidJdk already checks that the detected distribution in the provisioned JDK actually matches the
        // requested one (because it's the only one in the list in this test case).
        assertValidJdk(
            JdkProvisioningCriteria(
                majorVersion = 21,
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
     * Whether this distribution is only available on Linux (or more exotic OSes like z/OS or AIX that are irrelevant
     * to us).
     */
    private fun JvmDistribution.isLinuxOnly(): Boolean = this in setOf(
        JvmDistribution.Bisheng, // only Linux, see https://www.openeuler.org/en/other/projects/bishengjdk/
        JvmDistribution.IbmSemeruCertified, // only Linux, AIZ, z/OS, see https://developer.ibm.com/languages/java/semeru-runtimes/downloads/
    )

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
                operatingSystems = listOf(OperatingSystem.MACOS),
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
                operatingSystems = listOf(OperatingSystem.MACOS),
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
                operatingSystems = listOf(OperatingSystem.MACOS),
            )
        )
    }

    /**
     *  Some distributions for macOS have the actual JDK nested in the archive under a directory that has some sibling
     *  directories (so we can't just go through single dirs to find the JDK). One distinctive aspect is that this
     *  directory contains the word "jdk".
     *
     *  Example: OpenLogic 8
     *
     *  ```
     *  openlogic-openjdk-8u462-b08-mac-x64
     *  ├─ bin
     *  │  ├─ javafxpackager
     *  │  ╰─ javapackager
     *  │     // no other binary - no java, no javac!
     *  ├─ jdk1.8.0_462.jdk
     *  │  ╰─ Contents
     *  │     ├─ Home
     *  │     │  ╰─ <jdk here>
     *  │     ├─ MacOS
     *  │     ╰─ info.plist
     *  ├─ jre
     *  ├─ lib
     *  ╰─ man
     *  ```
     */
    @Test
    fun provisionJdk_nestedJdkAmongOtherDirs() = runTest(timeout = 10.minutes) {
        assertValidJdk(
            JdkProvisioningCriteria(
                majorVersion = 8,
                distributions = listOf(JvmDistribution.PerforceOpenLogic),
                operatingSystems = listOf(OperatingSystem.MACOS),
                architectures = listOf(Architecture.X86_64), // pinned so the test output doesn't vary depending on the host
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
                operatingSystems = listOf(OperatingSystem.MACOS),
            )
        )
    }

    // When asking for BiSheng 11 via the DiscoAPI, it returns multiple results, one of which is a "debuginfo" package
    // that's not a real JDK. These should be ignored by our JDK provisioning, and the valid JDK should be selected.
    @Test
    fun provisionJdk_debuginfoPackageInTheResults_shouldBeFilteredOut() = runTest(timeout = 10.minutes) {
        assertValidJdk(
            JdkProvisioningCriteria(
                majorVersion = 11,
                distributions = listOf(JvmDistribution.Bisheng),
                operatingSystems = listOf(OperatingSystem.LINUX),
            )
        )
    }

    @Test
    fun provisionJdk_failsWithNoResults() = runTest(timeout = 3.minutes) {
        val criteria = JdkProvisioningCriteria(
            majorVersion = 11,
            distributions = listOf(JvmDistribution.Bisheng),
            operatingSystems = listOf(OperatingSystem.WINDOWS), // no BiSheng for Windows
            architectures = listOf(Architecture.X86_64), // pinned so the test output doesn't vary depending on the host
        )
        val jdkResult = createTestJdkProvider().provisionJdk(criteria)
        val failureMessage = """
            Could not find any JDK that match the criteria:
              - Major version: 11
              - Operating system(s): windows
              - Architecture(s): x86_64
              - LibC type(s): c_std_lib
              - Acceptable distribution(s): bisheng
        """.trimIndent()
        assertEquals(JdkResult.Failure(failureMessage), jdkResult)
    }

    /**
     * Asserts a JDK can be provisioned, that it is a valid JDK, and that it matches the criteria used to provision it.
     */
    private suspend fun assertValidJdk(jdkProvisioningCriteria: JdkProvisioningCriteria) {
        val jdkResult = createTestJdkProvider().provisionJdk(jdkProvisioningCriteria)
        val jdk = when (jdkResult) {
            is JdkResult.Failure -> fail("JDK should be resolved successfully but failed with: ${jdkResult.message}")
            is JdkResult.Success -> jdkResult.jdk
        }
        assertTrue(
            jdk.version.startsWith(jdkProvisioningCriteria.majorVersion.toString()),
            "the provisioned JDK version should match the major version criteria",
        )
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
