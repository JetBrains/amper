/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.android

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.downloader.Downloader
import org.jetbrains.amper.core.downloader.suspendingRetryWithExponentialBackOff
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.core.extract.extractZip
import org.jetbrains.amper.jvm.Jdk
import org.jetbrains.amper.jvm.JdkDownloader
import org.jetbrains.amper.processes.runProcessWithInheritedIO
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.io.path.appendLines
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readLines

@Suppress("ReplacePrintlnWithLogging") // these print statements are for tests, and thus OK
internal object AndroidToolsInstaller {

    // from https://github.com/thyrlian/AndroidSDK/blob/master/android-sdk/license-accepter.sh
    private val licensesToAccept: List<Pair<String, String>> = listOf(
        "android-googletv-license" to "601085b94cd77f0b54ff86406957099ebe79c4d6",
        "android-sdk-license" to "8933bad161af4178b1185d1a37fbf41ea5269c55",
        "android-sdk-license" to "d56f5187479451eabf01fb78af6dfcb131a6481e",
        "android-sdk-license" to "24333f8a63b6825ea9c5514f83c2829b004d1fee",
        "android-sdk-preview-license" to "84831b9409646a918e30573bab4c9c91346d8abd",
        "android-sdk-preview-license" to "504667f4c0de7af1a06de9f4b1727b84351f2910",
        "google-gdk-license" to "33b6a2b64607f11b759f320ef9dff4ae5c47d97a",
        "intel-android-extra-license" to "d975f751698a77b662f1254ddbeed3901e976f5a",
        "android-sdk-arm-dbt-license" to "859f317696f67ef3d7f30a50a5560e7834b43903",
    )

    private val toolsToInstall = listOf(
        "cmdline-tools;latest", // the tests download the latest anyway, so we pre-download it to avoid 100% cache miss
        "platform-tools",
        "platforms;android-31",
        "platforms;android-33",
        "platforms;android-34",
        "platforms;android-35",
        "build-tools;33.0.0",
        "build-tools;33.0.1",
        "build-tools;34.0.0",
        "build-tools;35.0.0",
    )

    suspend fun prepareAndroidSdkHome(androidSdkHome: Path, androidSetupCacheDir: Path) {
        val commandLineToolsZip = Downloader.downloadFileToCacheLocation(
            // We use the same one on all platforms to benefit from caching.
            // It's ok because we use the JDK to directly run it instead of the platform-specific wrapper scripts
            url = "https://cache-redirector.jetbrains.com/dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip",
            userCacheRoot = AmperUserCacheRoot(androidSetupCacheDir),
        )

        val configuration = mapOf(
            "androidSdkHomePath" to androidSdkHome.pathString,
            "cmdlineToolsRevision" to getCmdlineToolsPkgRevision(commandLineToolsZip),
            "licensesToAccept" to licensesToAccept.joinToString(" ") { "${it.first}-${it.second}" },
            "toolsToInstall" to toolsToInstall.joinToString(" "),
        )

        ExecuteOnChangedInputs(
            buildOutputRoot = AmperBuildOutputRoot(androidSetupCacheDir),
            // We override the number here so that local changes DON'T invalidate the cache for this specific case.
            // The idea is that, the vast majority of the time, nothing changes in how we download/store the SDK
            // tools, so we don't want to re-download everything after every single change.
            currentAmperBuildNumber = AmperBuild.mavenVersion,
        ).execute(
            id = "android-sdk",
            configuration = configuration,
            inputs = emptyList()
        ) {
            cleanDirectory(androidSdkHome)
            installAndroidToolsTo(
                targetAndroidHome = androidSdkHome,
                commandLineToolsZip = commandLineToolsZip,
                androidSetupCacheDir = androidSetupCacheDir,
            )

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(androidSdkHome))
        }.outputs.single()
    }

    private fun getCmdlineToolsPkgRevision(commandLineToolsZip: Path): String {
        val cmdToolsSourceProperties = ZipFile(commandLineToolsZip.toFile()).use { zip ->
            zip.getInputStream(zip.getEntry("cmdline-tools/source.properties")!!).use { zipStream ->
                Properties().also { props -> props.load(zipStream) }
            }
        }
        val revision: String? = cmdToolsSourceProperties.getProperty("Pkg.Revision")
        check(!revision.isNullOrBlank()) {
            "Unable to get Pkg.Revision from $commandLineToolsZip"
        }
        return revision
    }

    private suspend fun installAndroidToolsTo(
        targetAndroidHome: Path,
        commandLineToolsZip: Path,
        androidSetupCacheDir: Path,
    ) {
        extractZip(archiveFile = commandLineToolsZip, target = targetAndroidHome / "cmdline-tools", stripRoot = true)

        licensesToAccept.forEach { (name, hash) ->
            acceptAndroidLicense(targetAndroidHome, name, hash)
        }

        // we need a JDK to run the Java-based Android command line tools
        val jdk = JdkDownloader.getJdk(AmperUserCacheRoot(androidSetupCacheDir))
        for (tool in toolsToInstall) {
            suspendingRetryWithExponentialBackOff(backOffLimitMs = TimeUnit.MINUTES.toMillis(1)) {
                installTool(tool, targetAndroidHome, jdk)
            }
            // Such lock files are generated by Amper while running tests. Creating them up front makes them
            // part of the incremental cache, and thus we avoid the 100% cache miss situation.
            targetAndroidHome.resolve("$tool.lock").createFile()
        }
    }

    private fun acceptAndroidLicense(androidSdkHome: Path, name: String, hash: String) {
        val licenseFile = androidSdkHome / "licenses" / name
        licenseFile.parent.createDirectories()

        if (!licenseFile.exists()) {
            licenseFile.createFile()
        }
        if (hash !in licenseFile.readLines()) {
            licenseFile.appendLines(listOf(hash))
        }
    }

    /**
     * Installs the given [toolPackage] to the given [androidHome] using the SDK manager.
     * The SDK manager is expected to be already present in [androidHome]. It is run using the provided [jdk].
     */
    private suspend fun installTool(toolPackage: String, androidHome: Path, jdk: Jdk) {
        val java = jdk.javaExecutable
        val sdkManagerJar = androidHome / "cmdline-tools" / "lib" / "sdkmanager-classpath.jar"

        println("Installing '$toolPackage' into '$androidHome'...")

        // We can't use the platform-specific wrapper scripts to run the SDK manager because we download the same
        // distribution of the command line tools on every machine (for cache sharing purposes), not the one for the
        // current platform. This is why we need to use java to directly run the SDK manager jar.
        val cmd = listOf(
            java.pathString,
            "-cp",
            sdkManagerJar.pathString,
            "com.android.sdklib.tool.sdkmanager.SdkManagerCli",
            "--sdk_root=$androidHome",
            toolPackage,
        )
        println("  running ${cmd.joinToString("|")}")

        val rc = runProcessWithInheritedIO(command = cmd)
        if (rc != 0) {
            error("Android SdkManager failed with exit code $rc while executing: ${cmd.joinToString("|")}")
        }
    }
}