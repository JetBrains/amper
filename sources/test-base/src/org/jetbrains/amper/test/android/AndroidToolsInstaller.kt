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
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.jvm.Jdk
import org.jetbrains.amper.jvm.JdkDownloader
import org.jetbrains.amper.processes.runProcessWithInheritedIO
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.io.path.appendLines
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

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
        "system-images;android-35;default;x86_64", // to create AVDs automatically in mobile-tests
    )

    suspend fun prepareAndroidSdkHome(androidSdkHome: Path, androidSetupCacheDir: Path) {
        val commandLineToolsZip = downloadCommandLineToolsZip(androidSetupCacheDir)

        val configuration = mapOf(
            "androidSdkHomePath" to androidSdkHome.pathString,
            "cmdlineToolsRevision" to getCmdlineToolsPkgRevision(commandLineToolsZip),
            "licensesToAccept" to licensesToAccept.joinToString(" ") { "${it.first}-${it.second}" },
            "toolsToInstall" to toolsToInstall.joinToString(" "),
        )

        // This file changes on commands like 'create avd', so we remove it to avoid a cache miss.
        // (This is safe to do because it's automatically created if absent)
        androidSdkHome.resolve(".knownpackages").deleteIfExists()

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

    private suspend fun downloadCommandLineToolsZip(androidSetupCacheDir: Path): Path {
        val commandLineToolsFilename = when (DefaultSystemInfo.detect().family) {
            OsFamily.Linux,
            OsFamily.FreeBSD,
            OsFamily.Solaris -> "commandlinetools-linux-11076708_latest.zip"

            OsFamily.MacOs -> "commandlinetools-mac-11076708_latest.zip"
            OsFamily.Windows -> "commandlinetools-win-11076708_latest.zip"
        }
        return Downloader.downloadFileToCacheLocation(
            url = "https://cache-redirector.jetbrains.com/dl.google.com/android/repository/$commandLineToolsFilename",
            userCacheRoot = AmperUserCacheRoot(androidSetupCacheDir),
        )
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
        val cmdlineToolsDir = targetAndroidHome / "cmdline-tools"
        extractZip(archiveFile = commandLineToolsZip, target = cmdlineToolsDir, stripRoot = true)

        // Workaround for the SDK bug https://issuetracker.google.com/issues/391118558
        // (cmdline-tools scripts fail on directories with spaces, which we use in our tests)
        // We need to fix the scripts so we can use the sdkmanager to install the other required Android tools
        fixQuotingInScripts(cmdlineToolsDir / "bin")

        licensesToAccept.forEach { (name, hash) ->
            acceptAndroidLicense(targetAndroidHome, name, hash)
        }

        // we need a JDK to run the Java-based Android command line tools
        val jdk = JdkDownloader.getJdk(AmperUserCacheRoot(androidSetupCacheDir))
        for (tool in toolsToInstall) {
            suspendingRetryWithExponentialBackOff(backOffLimitMs = TimeUnit.MINUTES.toMillis(1)) {
                installTool(tool, targetAndroidHome, jdk)
            }
            // This file changes on commands like 'create avd', so we remove it to avoid a cache miss.
            // (This is safe to do because it's automatically created if absent)
            targetAndroidHome.resolve(".knownpackages").deleteIfExists()
            // Such lock files are generated by Amper while running tests. Creating them up front makes them
            // part of the incremental cache, and thus we avoid the 100% cache miss situation.
            targetAndroidHome.resolve("$tool.lock").createFile()
        }

        // We also need to fix the 'latest' cmdline-tools scripts after installation,
        // because these are the scripts that will actually be used in tests
        fixQuotingInScripts(cmdlineToolsDir / "latest/bin")
    }

    // Workaround for the SDK bug https://issuetracker.google.com/issues/391118558
    // (cmdline-tools scripts fail on directories with spaces, which we use in our tests)
    private fun fixQuotingInScripts(scriptsDir: Path) {
        scriptsDir.listDirectoryEntries().forEach { script ->
            val fixedContent = script
                .readText()
                .replace(
                    oldValue = "DEFAULT_JVM_OPTS='-Dcom.android.sdklib.toolsdir=\$APP_HOME'",
                    newValue = "DEFAULT_JVM_OPTS='-Dcom.android.sdklib.toolsdir=\"\$APP_HOME\"'"
                )
            script.writeText(fixedContent)
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
        val sdkManagerFilename = if (DefaultSystemInfo.detect().family.isWindows) "sdkmanager.bat" else "sdkmanager"
        val sdkManagerExecPath = androidHome / "cmdline-tools" / "bin" / sdkManagerFilename

        println("Installing '$toolPackage' into '$androidHome'...")

        val cmd = listOf(sdkManagerExecPath.pathString, "--sdk_root=$androidHome", toolPackage)
        val rc = runProcessWithInheritedIO(
            command = cmd,
            environment = mapOf(
                "JAVA_HOME" to jdk.homeDir.pathString,
                "ANDROID_HOME" to androidHome.pathString,
            ),
        )
        if (rc != 0) {
            error("Android SDK Manager failed with exit code $rc while executing: ${cmd.joinToString("|")}")
        }
    }
}