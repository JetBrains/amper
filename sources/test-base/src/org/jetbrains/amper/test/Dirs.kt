/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.dependency.resolution.LocalM2RepositoryFinder
import org.jetbrains.amper.test.android.AndroidToolsInstaller
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
    val amperTestProjectsRoot = amperSourcesRoot / "amper-backend-test/testData/projects"

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
    private val sharedTestCaches: Path by lazy {
        // Always run tests in a directory with a space in the name, tests quoting in a lot of places
        val dir = if (TeamCityHelper.isUnderTeamCity) {
            val persistentCachePath = TeamCityHelper.systemProperties["agent.persistent.cache"]
            check(!persistentCachePath.isNullOrBlank()) {
                "'agent.persistent.cache' system property is required under TeamCity"
            }
            Path(persistentCachePath) / "amper build"
        } else {
            amperCheckoutRoot / "shared test caches"
        }

        dir.createDirectories()
    }

    /**
     * Path to a directory used as Amper user cache, reused across test executions.
     */
    val sharedAmperCacheRoot by lazy { sharedTestCaches / "amper-cache" }

    /**
     * Path to a directory used as Gradle home, reused across test executions.
     */
    val sharedGradleHome by lazy { (sharedTestCaches / "gradleHome").createDirectories() }

    /**
     * Path to a temporary directory that may or may not be reused across test runs.
     *
     * * on dev machines: some place in the working copy, assuming it won't be cleared after every test run
     * * on TeamCity: some place that is removed after the build
     */
    val tempDir: Path by lazy {
        val dir = if (TeamCityHelper.isUnderTeamCity) {
            // As we found out, tempDirectory from TeamCity sometimes can be not empty (e.g., locked by some process).
            // Let's make it unique and add build id (global build counter on TC server across the entire server).
            TeamCityHelper.tempDirectory / TeamCityHelper.buildId / "amper tests"
        } else {
            amperCheckoutRoot / "build" / "tests temp"
        }
        dir.createDirectories()
        println("Temp dir for tests: $dir")
        dir
    }

    /**
     * Path to the root directory of a cache that is reused across test runs on dev machines, but not reused on CI.
     *
     * * on dev machines: the same as [sharedTestCaches]
     * * on TeamCity: some place that is removed after the build
     */
    val userCacheRoot: Path = if (TeamCityHelper.isUnderTeamCity) {
        // As we found out, tempDirectory from TeamCity sometimes could be not empty
        // (e.g., locked by some process)
        // let's make it unique and add build id (global build counter on TC server across the entire server)
        TeamCityHelper.tempDirectory.resolve(TeamCityHelper.buildId).resolve("amperUserCacheRoot").also {
            it.createDirectories()
        }
    } else {
        sharedAmperCacheRoot
    }

    val androidHome: Path by lazy {
        val androidSdkHome = sharedTestCaches / "android-sdk"
        runBlocking(Dispatchers.IO) {
            AndroidToolsInstaller.prepareAndroidSdkHome(
                androidSdkHome = androidSdkHome,
                androidSetupCacheDir = sharedTestCaches / "android-setup-cache",
            )
        }
        androidSdkHome
    }
}
