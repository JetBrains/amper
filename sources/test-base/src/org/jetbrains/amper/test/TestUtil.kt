/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.Jdk
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.downloader.Downloader
import org.jetbrains.amper.core.downloader.suspendingRetryWithExponentialBackOff
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.core.extract.extractZip
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

object TestUtil {

    // from https://github.com/thyrlian/AndroidSDK/blob/master/android-sdk/license_accepter.sh
    private val licensesToAccept: List<Pair<String, String>> = listOf(
        "android-googletv-license" to "601085b94cd77f0b54ff86406957099ebe79c4d6",
        "android-sdk-license" to "8933bad161af4178b1185d1a37fbf41ea5269c55",
        "android-sdk-license" to "d56f5187479451eabf01fb78af6dfcb131a6481e",
        "android-sdk-license" to "24333f8a63b6825ea9c5514f83c2829b004d1fee",
        "android-sdk-preview-license" to "84831b9409646a918e30573bab4c9c91346d8abd",
        "android-sdk-preview-license" to "504667f4c0de7af1a06de9f4b1727b84351f2910",
        "google-gdk-license" to "33b6a2b64607f11b759f320ef9dff4ae5c47d97a",
        "intel-android-extra-license" to "d975f751698a77b662f1254ddbeed3901e976f5a",
    )

    private val toolsToInstall = listOf(
        "cmdline-tools;latest", // the tests download the latest anyway, so we pre-download it for our incrementality
        "platform-tools",
        "platforms;android-31",
        "platforms;android-33",
        "platforms;android-34",
        "build-tools;33.0.0",
        "build-tools;33.0.1",
        "build-tools;34.0.0",
    )

    val amperCheckoutRoot: Path by lazy {
        val start = Path.of(System.getProperty("user.dir"))

        var current: Path = start
        while (current != current.parent) {
            if (current.resolve("syncVersions.sh").exists() && current.resolve("CONTRIBUTING.md").exists()) {
                return@lazy current
            }

            current = current.parent ?: break
        }

        error("Unable to find Amper checkout root upwards from '$start'")
    }

    val amperSourcesRoot = amperCheckoutRoot / "sources"

    val m2 = Path.of(System.getProperty("user.home"), ".m2")
    val m2repository = m2.resolve("repository")

    // Shared between different runs of testing
    // on developer's machine: some place under working copy, assuming it won't be cleared after every test run
    // on TeamCity: shared place on build agent which will be fully deleted if TeamCity lacks space on that agent
    // Always run tests in a directory with a space in the name, tests quoting in a lot of places
    val sharedTestCaches: Path by lazy {
        val dir = if (TeamCityHelper.isUnderTeamCity) {
            val persistentCachePath = TeamCityHelper.systemProperties["agent.persistent.cache"]
            check(!persistentCachePath.isNullOrBlank()) {
                "'agent.persistent.cache' system property is required under TeamCity"
            }
            Paths.get(persistentCachePath) / "amper build"
        } else {
            amperCheckoutRoot / "shared test caches"
        }

        Files.createDirectories(dir)
    }

    // Always run tests in a directory with a space in the name, tests quoting in a lot of places
    // on developer's machine: some place under working copy, assuming it won't be cleared after every test run
    // on TeamCity: will be removed after the build
    val tempDir: Path by lazy {
        val dir = if (TeamCityHelper.isUnderTeamCity) {
            // As we found out, tempDirectory from TeamCity sometimes could be not empty
            // (e.g., locked by some process)
            // let's make it unique and add build id (global build counter on TC server across the entire server)
            TeamCityHelper.tempDirectory / TeamCityHelper.buildId / "amper tests"
        } else {
            amperCheckoutRoot / "build" / "tests temp"
        }
        Files.createDirectories(dir)
        println("Temp dir for tests: $dir")
        dir
    }

    // Re-use user cache root for local runs to make testing faster
    // On CI (TeamCity) make it per-build (temp directory for build is cleaned after each build run)
    val userCacheRoot: Path = if (TeamCityHelper.isUnderTeamCity) {
        // As we found out, tempDirectory from TeamCity sometimes could be not empty
        // (e.g., locked by some process)
        // let's make it unique and add build id (global build counter on TC server across the entire server)
        TeamCityHelper.tempDirectory.resolve(TeamCityHelper.buildId).resolve("amperUserCacheRoot").also {
            it.createDirectories()
        }
    } else sharedTestCaches


    val androidHome: Path by lazy {
        runBlocking(Dispatchers.IO) {
            val fakeUserCacheRoot = AmperUserCacheRoot(sharedTestCaches)
            val fakeBuildOutputRoot = AmperBuildOutputRoot(sharedTestCaches)

            val androidSdkHome = fakeUserCacheRoot.path / "android-sdk"

            // If we leave them, the incremental state check will fail, and we'll reinstall all the tools.
            removeTestRunLeftovers(androidSdkHome)

            val commandLineTools = suspendingRetryWithExponentialBackOff {
                Downloader.downloadFileToCacheLocation(
                    url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip",
                    userCacheRoot = fakeUserCacheRoot,
                )
            }

            val configuration = mapOf(
                "cmdToolsRevision" to getCmdToolsPkgRevision(commandLineTools),
                "licenses" to licensesToAccept.joinToString(" ") { "${it.first}-${it.second}" },
                "packages" to toolsToInstall.joinToString(" "),
            )

            ExecuteOnChangedInputs(fakeBuildOutputRoot).execute(
                "android-sdk",
                configuration,
                inputs = emptyList()
            ) {
                cleanDirectory(androidSdkHome)

                extractZip(commandLineTools, androidSdkHome / "cmdline-tools", true)

                licensesToAccept.forEach { (name, hash) ->
                    acceptAndroidLicense(androidSdkHome, name, hash)
                }

                val jdk = JdkDownloader.getJdk(fakeUserCacheRoot)
                for (tool in toolsToInstall) {
                    suspendingRetryWithExponentialBackOff(backOffLimitMs = TimeUnit.MINUTES.toMillis(1)) {
                        installTool(jdk, androidSdkHome, tool)
                    }
                }

                return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(androidSdkHome))
            }.outputs.single()

            return@runBlocking androidSdkHome
        }
    }

    private fun removeTestRunLeftovers(androidSdkHome: Path) {
        if (!androidSdkHome.exists()) {
            return
        }
        // When running tests, some .lock files are generated in the Android home and can be left over.
        androidSdkHome.listDirectoryEntries("*.lock").forEach {
            it.deleteExisting()
        }
    }

    private fun getCmdToolsPkgRevision(commandLineToolsZip: Path): String {
        val cmdToolsSourceProperties = ZipFile(commandLineToolsZip.toFile()).use { zip ->
            zip.getInputStream(zip.getEntry("cmdline-tools/source.properties")!!).readAllBytes()
        }
        val props = Properties().also { it.load(ByteArrayInputStream(cmdToolsSourceProperties)) }
        val cmdToolsPkgRevision: String? = props.getProperty("Pkg.Revision")
        check(!cmdToolsPkgRevision.isNullOrBlank()) {
            "Unable to get Pkg.Revision from $commandLineToolsZip"
        }

        return cmdToolsPkgRevision
    }

    private fun acceptAndroidLicense(androidSdkHome: Path, name: String, hash: String) {
        val licenseFile = androidSdkHome / "licenses" / name
        Files.createDirectories(licenseFile.parent)

        if (!licenseFile.isRegularFile() || licenseFile.readText() != hash) {
            licenseFile.writeText(hash)
        }
    }

    private suspend fun installTool(jdk: Jdk, androidHome: Path, toolPackage: String) {
        val java = jdk.javaExecutable
        val sdkManagerJar = androidHome / "cmdline-tools" / "lib" / "sdkmanager-classpath.jar"

        println("Installing '$toolPackage' into '$androidHome'...")

        val cmd = listOf(
            java.pathString,
            "-cp",
            sdkManagerJar.pathString,
            "com.android.sdklib.tool.sdkmanager.SdkManagerCli",
            "--sdk_root=$androidHome",
            toolPackage,
        )
        println("  running ${cmd.joinToString("|")}")

        runInterruptible {
            val pb = ProcessBuilder()
                .command(*cmd.toTypedArray())
                .inheritIO()
                .start()
            val rc = pb.waitFor()
            if (rc != 0) {
                error("Android SdkManager failed with exit code $rc while executing: ${cmd.joinToString("|")}")
            }
        }
    }
}
