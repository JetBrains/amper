/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.dependency.resolution.LocalM2RepositoryFinder
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists

object Dirs {

    /**
     * The root directory of the Amper project, which is the checked out repository directory.
     */
    val amperCheckoutRoot: Path by lazy {
        val start = Path(System.getProperty("user.dir"))

        generateSequence(start) { it.parent }
            .find { (it / ".github").exists() && (it / "CONTRIBUTING.md").exists() }
            ?: error("Unable to find Amper checkout root upwards from '$start'")
    }

    /**
     * The `sources` directory in the Amper project, containing all submodules of Amper.
     */
    val amperSourcesRoot = amperCheckoutRoot / "sources"

    /**
     * The directory containing all test projects for Amper standalone.
     */
    val amperTestProjectsRoot = amperSourcesRoot / "test-integration/test-projects"

    /**
     * The location of the local maven repository.
     */
    val m2repository = LocalM2RepositoryFinder.findPath()

    /**
     * Path to the root directory of a cache that is reused across test runs, and across CI builds.
     *
     * * on dev machines: some place in the working copy, assuming it won't be cleared after every test run
     * * on TeamCity: a shared place on the build agent, reused between builds but potentially fully deleted if
     *   TeamCity lacks space on that agent
     */
    private val persistentCaches: Path by lazy {
        // Always run tests in a directory with a space in the name, tests quoting in a lot of places
        val dir = if (TeamCityHelper.isUnderTeamCity) {
            // We add the OS + arch to avoid problems in case the cache is shared between different types of machines.
            // Example: the incremental cache contains paths in Windows style on Windows, and unix style on other OSes.
            TeamCityHelper.persistentCacheDirectory / "amper build" / DefaultSystemInfo.detect().familyArch
        } else {
            amperCheckoutRoot / "shared test caches"
        }

        dir.createDirectories()
    }

    /**
     * Path to the root directory of a cache that is reused across test runs on dev machines, but not reused across CI
     * builds.
     *
     * * on dev machines: the same as [persistentCaches]
     * * on TeamCity: some place that is clean on every build
     */
    private val semiPersistentCaches: Path by lazy {
        if (TeamCityHelper.isUnderTeamCity) {
            TeamCityHelper.cleanTempDirectory
        } else {
            persistentCaches
        }
    }

    /**
     * Path to a directory used to cache HTTP downloads done in tests, reused across test executions and CI builds.
     */
    val persistentHttpCache by lazy { persistentCaches / "http-cache" }

    /**
     * Path to a directory used as Gradle home, reused across test executions, but not reused on the CI.
     */
    val sharedGradleHome by lazy { (semiPersistentCaches / "gradle-home").createDirectories() }

    /**
     * Path to a temporary directory that may or may not be reused across test runs.
     *
     * * on dev machines: some place in the working copy, assuming it won't be cleared after every test run
     * * on TeamCity: some place that is clean on every build
     */
    val tempDir: Path by lazy {
        val dir = if (TeamCityHelper.isUnderTeamCity) {
            // As we found out, tempDirectory from TeamCity sometimes can be not empty (e.g., locked by some process).
            // Let's make it unique and add build id (global build counter on TC server across the entire server).
            TeamCityHelper.cleanTempDirectory / "amper tests"
        } else {
            amperCheckoutRoot / "build" / "tests temp"
        }
        dir.createDirectories()
        println("Temp dir for tests: $dir")
        dir
    }

    /**
     * Path to the root directory of a cache that is reused across test runs on dev machines, but not reused across CI
     * builds.
     *
     * * on dev machines: some place in the working copy, assuming it won't be cleared after every test run
     * * on TeamCity: some place that is removed after the build
     */
    val userCacheRoot: Path by lazy { semiPersistentCaches / "amper-cache" }

    /**
     * A root directory to store Android SDK data and setup caches, reused between test runs and between CI builds.
     *
     * **Note:** consumers should generally prefer [org.jetbrains.amper.test.android.AndroidTools.getOrInstallForTests].
     */
    internal val androidTestCache: Path by lazy { persistentCaches / "android" }
}
