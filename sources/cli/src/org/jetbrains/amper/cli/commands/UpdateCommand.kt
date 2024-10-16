/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.terminal.prompt
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.downloader.httpClient
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

internal class UpdateCommand : AmperSubcommand(name = "update") {

    private val repository by option(
        "-r", "--repository",
        help = "URL of the maven repository to download the Amper scripts from",
    ).default("https://packages.jetbrains.team/maven/p/amper/amper")

    private val useDevVersion by option(
        "--dev",
        help = "Use the latest development version instead of the official release",
    ).flag(default = false)

    private val targetVersion by option(
        "--target-version", // avoid --version to avoid confusion with the "./amper --version" command
        help = "The specific version to update to. By default, the latest version is used.",
    )

    override fun help(context: Context): String = "Update Amper to the latest version"

    override suspend fun run() {
        // We could in theory find the parent dir of the actual script that launched Amper (even without project root
        // discovery, by passing more info from the wrapper to Amper), but the benefit would be marginal, and it would
        // break amper-from-sources.
        // Also, we would still have to respect an explicit --root option to allow users to update other projects.
        val targetDir = commonOptions.explicitRoot ?: Path(".")
        val amperBashPath = targetDir.resolve("amper")
        val amperBatPath = targetDir.resolve("amper.bat")
        checkDirectories(amperBashPath, amperBatPath)
        confirmUpdateOnMissingWrappers(amperBashPath, amperBatPath)

        val version = targetVersion ?: getLatestVersion()

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
    }

    private fun checkDirectories(vararg amperScriptPaths: Path) {
        val clashingDirs = amperScriptPaths.filter { it.exists() && it.isDirectory() }
        if (clashingDirs.isNotEmpty()) {
            userReadableError("Amper scripts cannot be updated because a directory with a conflicting name exists: " +
                    clashingDirs.first().normalize().absolutePathString()
            )
        }
    }

    private fun confirmUpdateOnMissingWrappers(vararg amperScriptPaths: Path) {
        val missingScripts = amperScriptPaths.filterNot { it.exists() }
        if (missingScripts.isEmpty()) {
            return
        }
        val targetDirRef = commonOptions.explicitRoot?.pathString ?: "the current directory"
        val prompt = if (missingScripts.size == amperScriptPaths.size) {
            "Amper scripts were not found in $targetDirRef.\nWould you like to generate them from scratch? (Y/n)"
        } else {
            "An Amper script is missing: ${missingScripts.first().normalize().absolutePathString()}.\nUpdating will generate it. Would you like to continue? (Y/n)"
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

    private suspend fun getLatestVersion(): String {
        commonOptions.terminal.println("Fetching latest Amper version info...")
        val metadataXml = fetchMavenMetadataXml()
        return versionRegex.findAll(metadataXml)
            .map { it.groupValues[1] }
            .filter { useDevVersion || "dev" !in it }
            .maxByOrNull { ComparableVersion(it) }
            ?.also {
                val versionMoniker = if (useDevVersion) "dev version of Amper" else "Amper version"
                commonOptions.terminal.println("Latest $versionMoniker is $it")
            }
            ?: userReadableError("Couldn't read Amper latest version from maven-metadata.xml:\n\n$metadataXml")
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
}

private val versionRegex = Regex("<version>(.+?)</version>")
