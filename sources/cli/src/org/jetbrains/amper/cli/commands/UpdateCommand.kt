/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.terminal.prompt
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.downloader.httpClient
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.processes.runProcessWithInheritedIO
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

private sealed class DesiredVersion {
    data class Latest(val includeDevVersions: Boolean) : DesiredVersion()
    data class SpecificVersion(val version: String) : DesiredVersion()
}

internal class UpdateCommand : AmperSubcommand(name = "update") {

    private val repository by option(
        "-r", "--repository",
        help = "URL of the maven repository to download the Amper scripts from",
    ).default("https://packages.jetbrains.team/maven/p/amper/amper")

    private val desiredVersion by mutuallyExclusiveOptions(
        option("--dev", help = "Use the latest development version instead of the official release")
            .flag()
            .convert { DesiredVersion.Latest(includeDevVersions = it) },
        // avoid --version to avoid confusion with the "./amper --version" command
        option("--target-version", help = "The specific version to update to. By default, the latest version is used.")
            .convert { DesiredVersion.SpecificVersion(it) },
    )
        .single() // fail if both --dev and --target-version are used at the same time
        .default(DesiredVersion.Latest(includeDevVersions = false))

    private val create by option("-c", "--create", help = "Create the Amper scripts if they don't exist yet")
        .flag()

    override fun help(context: Context): String = "Update Amper to the latest version"

    override suspend fun run() {
        // We could in theory find the parent dir of the actual script that launched Amper (even without project root
        // discovery, by passing more info from the wrapper to Amper), but the benefit would be marginal, and it would
        // break amper-from-sources.
        // Also, we would still have to respect an explicit --root option to allow users to update other projects.
        val targetDir = commonOptions.explicitRoot ?: Path(".")
        val amperBashPath = targetDir.resolve("amper")
        val amperBatPath = targetDir.resolve("amper.bat")
        checkNotDirectories(amperBashPath, amperBatPath)
        if (!create) {
            confirmCreateIfMissingWrappers(amperBashPath, amperBatPath)
        }

        val version = desiredVersion.resolve()

        commonOptions.terminal.println("Downloading Amper scripts...")
        // it's ok to load the whole wrapper content in memory (it's quite small)
        val bashWrapper = fetchWrapperContent(version = version, extension = "")
        val batWrapper = fetchWrapperContent(version = version, extension = ".bat")

        if (amperBashPath.exists() && bashWrapper == amperBashPath.readText() &&
            amperBatPath.exists() && batWrapper == amperBatPath.readText()) {
            commonOptions.terminal.println("Amper is already in version $version, nothing to update")
            return
        }

        // we only overwrite the files once we're sure we could fetch everything
        amperBashPath.writeText(bashWrapper)
        amperBatPath.writeText(batWrapper)

        commonOptions.terminal.println("Updated Amper scripts to version $version")

        // to force downloading Amper distribution and JRE
        val exitCode = runAmperVersionFirstRun(amperBatPath, amperBashPath)
        if (exitCode != 0) {
            userReadableError("Couldn't run the new Amper version. Please check the errors above.")
        }
    }

    private fun checkNotDirectories(vararg amperScriptPaths: Path) {
        val clashingDirs = amperScriptPaths.filter { it.exists() && it.isDirectory() }
        if (clashingDirs.isNotEmpty()) {
            userReadableError("Amper scripts cannot be updated because a directory with a conflicting name exists: " +
                    clashingDirs.first().normalize().absolutePathString()
            )
        }
    }

    private fun confirmCreateIfMissingWrappers(vararg amperScriptPaths: Path) {
        val missingScripts = amperScriptPaths.filterNot { it.exists() }
        if (missingScripts.isEmpty()) {
            return
        }
        val targetDirRef = commonOptions.explicitRoot?.pathString ?: "the current directory"
        val prompt = if (missingScripts.size == amperScriptPaths.size) {
            "Amper scripts were not found in $targetDirRef.\nWould you like to create them from scratch? (Y/n)"
        } else {
            "An Amper script is missing: ${missingScripts.first().normalize().absolutePathString()}.\nUpdating will create it. Would you like to continue? (Y/n)"
        }
        val answer = commonOptions.terminal.prompt(
            prompt = prompt,
            default = "y",
            showChoices = false,
            showDefault = false,
            choices = listOf("y", "Y", "n", "N"),
        )
        if (answer?.lowercase() != "y") {
            commonOptions.terminal.println("Update aborted.")
            exitProcess(0)
        }
    }

    private suspend fun DesiredVersion.resolve() = when (this) {
        is DesiredVersion.Latest -> getLatestVersion(includeDevVersions = includeDevVersions)
        is DesiredVersion.SpecificVersion -> version
    }

    private suspend fun getLatestVersion(includeDevVersions: Boolean): String {
        commonOptions.terminal.println("Fetching latest Amper version info...")
        val metadataXml = fetchMavenMetadataXml()
        return xmlVersionElementRegex.findAll(metadataXml)
            .mapNotNull { parseAmperVersion(it.groupValues[1]) }
            .filter { !it.isDevVersion || (includeDevVersions && !it.isSpecialBranchVersion) }
            .maxByOrNull { ComparableVersion(it.fullMavenVersion) }
            ?.fullMavenVersion
            ?.also {
                val versionMoniker = if (includeDevVersions) "dev version of Amper" else "Amper version"
                commonOptions.terminal.println("Latest $versionMoniker is $it")
            }
            ?: userReadableError("Couldn't read Amper versions from maven-metadata.xml:\n\n$metadataXml")
    }

    private suspend fun fetchMavenMetadataXml(): String = try {
        httpClient.get("$repository/org/jetbrains/amper/cli/maven-metadata.xml").bodyAsText()
    } catch (e: Exception) {
        userReadableError("Couldn't fetch the latest Amper version:\n$e")
    }

    private suspend fun fetchWrapperContent(version: String, extension: String): String = try {
        httpClient.get("$repository/org/jetbrains/amper/cli/$version/cli-$version-wrapper$extension").bodyAsText()
    } catch (e: Exception) {
        userReadableError("Couldn't fetch Amper script content in version $version:\n$e")
    }

    private suspend fun runAmperVersionFirstRun(batWrapper: Path, bashWrapper: Path): Int {
        val wrapper = if (DefaultSystemInfo.detect().family.isWindows) batWrapper else bashWrapper
        return runProcessWithInheritedIO(command = listOf(wrapper.absolutePathString(), "--version"))
    }
}

private val xmlVersionElementRegex = Regex("<version>(.+?)</version>")

private data class AmperVersion(
    val versionTriplet: String,
    val devBuildNumber: String?,
    val branchSuffix: String?,
    val fullMavenVersion: String,
) {
    val isDevVersion get() = devBuildNumber != null
    val isSpecialBranchVersion get() = branchSuffix != null
}

private val versionRegex = Regex("""(?<versionTriplet>[^-]+)(-dev-(?<build>\d+)(-(?<branchSuffix>.*))?)?""")

private fun parseAmperVersion(version: String): AmperVersion? {
    val versionMatch = versionRegex.matchEntire(version) ?: return null
    return AmperVersion(
        versionTriplet = versionMatch.groups["versionTriplet"]?.value ?: error("versionTriplet is mandatory"),
        devBuildNumber = versionMatch.groups["build"]?.value,
        branchSuffix = versionMatch.groups["branchSuffix"]?.value,
        fullMavenVersion = version,
    )
}
