/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.prompt
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.downloader.Downloader
import org.jetbrains.amper.core.downloader.httpClient
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.processes.ProcessLeak
import org.jetbrains.amper.processes.runProcessWithInheritedIO
import org.jetbrains.amper.processes.startLongLivedProcess
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.util.ShellQuoting
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission.*
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileAttributesViewOrNull
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.isSameFileAs
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.system.exitProcess

private sealed class DesiredVersion {
    data class Latest(val includeDevVersions: Boolean) : DesiredVersion()
    data class SpecificVersion(val version: String) : DesiredVersion()
}

internal class UpdateCommand : AmperSubcommand(name = "update") {

    private val repository by option(
        "-r", "--repository",
        help = "The URL of the maven repository to download the Amper scripts from",
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

    private val runningWrapper by lazy { Path(System.getProperty("amper.wrapper.path")).absolute() }

    @OptIn(ProcessLeak::class)
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

        terminal.println("Downloading Amper scripts...")
        val newBashPath = downloadWrapper(version = version, extension = "").apply { setReadExecPermissions() }
        val newBatPath = downloadWrapper(version = version, extension = ".bat").apply { setReadExecPermissions() }
        terminal.println("Download complete.")

        if (amperBashPath.exists() && newBashPath.readText() == amperBashPath.readText() &&
            amperBatPath.exists() && newBatPath.readText() == amperBatPath.readText()) {
            terminal.println("Amper is already in version $version, nothing to update")
            return
        }

        // Test the new script and download the Amper distribution and JRE
        val exitCode = spanBuilder("New version first run").use {
            runAmperVersionFirstRun(newBatPath, newBashPath)
        }
        if (exitCode != 0) {
            userReadableError("Couldn't run the new Amper version. Please check the errors above.")
        }

        // Replacing a bash script while it's running is possible. We use move commands to ensure the physical file on
        // disk is not modified, thus we can write a new physical file to the old location. Bash will keep loading the
        // old file incrementally from the old physical file using its old file descriptor, which is good.
        spanBuilder("Replace 'amper' script (bash)").use {
            copyAndReplaceSafely(source = newBashPath, target = amperBashPath)
        }

        // Batch files are different. When running, cmd.exe reloads the file after each command and tries to resume at
        // whatever byte offset it was. We can modify the file while it's running, but when the java command running
        // this code completes, it will resume in the new wrapper code. If the new script is shorter, cmd.exe will just
        // stop and the command completes normally. If the new script is longer, then cmd.exe will likely resume in the
        // middle of a command in the middle of the script, which will fail miserably.
        // Even with atomic moves, the new file is reloaded, so in this case we have to spawn a process that will
        // replace the old wrapper after the update command (and the current wrapper) finished executing.
        val batUpdateInPlaceWouldBreak = amperBatPath.exists()
                && amperBatPath.isSameFileAs(runningWrapper)
                && newBatPath.fileSize() > runningWrapper.fileSize()
        spanBuilder("Replace 'amper.bat' script").use { span ->
            if (batUpdateInPlaceWouldBreak) {
                copyAndReplaceLaterWindows(source = newBatPath, target = amperBatPath)
                span.addEvent("amper.bat script copy scheduled for after JVM shutdown")
            } else {
                copyAndReplaceSafely(source = newBatPath, target = amperBatPath)
            }
        }

        val successStyle = Theme.Default.success
        terminal.println(successStyle("Update successful"))
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
        val answer = terminal.prompt(
            prompt = prompt,
            default = "y",
            showChoices = false,
            showDefault = false,
            choices = listOf("y", "Y", "n", "N"),
        )
        if (answer?.lowercase() != "y") {
            terminal.println("Update aborted.")
            exitProcess(0)
        }
    }

    private suspend fun DesiredVersion.resolve() = when (this) {
        is DesiredVersion.Latest -> getLatestVersion(includeDevVersions = includeDevVersions)
        is DesiredVersion.SpecificVersion -> version
    }

    private suspend fun getLatestVersion(includeDevVersions: Boolean): String =
        spanBuilder("Fetch latest Amper version").use {
            terminal.println("Fetching latest Amper version info...")
            val metadataXml = fetchMavenMetadataXml()
            xmlVersionElementRegex.findAll(metadataXml)
                .mapNotNull { parseAmperVersion(it.groupValues[1]) }
                .filter { !it.isDevVersion || (includeDevVersions && !it.isSpecialBranchVersion) }
                .maxByOrNull { ComparableVersion(it.fullMavenVersion) }
                ?.fullMavenVersion
                ?.also {
                    val versionMoniker = if (includeDevVersions) "dev version of Amper" else "Amper version"
                    val infoStyle = Theme.Default.info
                    terminal.println("Latest $versionMoniker is ${infoStyle(it)}")
                }
                ?: userReadableError("Couldn't read Amper versions from maven-metadata.xml:\n\n$metadataXml")
        }

    private suspend fun fetchMavenMetadataXml(): String = try {
        httpClient.get("$repository/org/jetbrains/amper/cli/maven-metadata.xml").bodyAsText()
    } catch (e: Exception) {
        userReadableError("Couldn't fetch the latest Amper version:\n$e")
    }

    private suspend fun downloadWrapper(version: String, extension: String): Path = try {
        spanBuilder("Download wrapper script (amper$extension)").use {
            Downloader.downloadFileToCacheLocation(
                url = "$repository/org/jetbrains/amper/cli/$version/cli-$version-wrapper$extension",
                userCacheRoot = commonOptions.sharedCachesRoot,
                infoLog = false,
            )
        }
    } catch (e: Exception) {
        userReadableError("Couldn't fetch Amper script version $version:\n$e")
    }

    private suspend fun runAmperVersionFirstRun(batWrapper: Path, bashWrapper: Path): Int {
        val command = when (DefaultSystemInfo.detect().family) {
            OsFamily.Windows -> if (runningWrapper.extension == "bat") {
                listOf(batWrapper.absolutePathString(), "--version")
            } else {
                // If we're running the bash script on Windows (probably with Git Bash), we need to also use the bash
                // script here. For this, we need to call bash explicitly.
                // Finding the corresponding unix-style path is not trivial, so we instead use ./name and ensure we're
                // in the correct working directory when running the process.
                listOf("bash.exe", "-c", ShellQuoting.quoteArgumentsPosixShellWay(listOf("./${bashWrapper.name}", "--version")))
            }
            OsFamily.Linux,
            OsFamily.MacOs,
            OsFamily.FreeBSD,
            OsFamily.Solaris -> listOf(bashWrapper.absolutePathString(), "--version")
        }
        // This working dir is intentional to support a plain `./amper` in Windows Git bash (without paths shenanigans)
        return runProcessWithInheritedIO(workingDir = bashWrapper.absolute().parent, command = command)
    }

    private fun Path.setReadExecPermissions() {
        fileAttributesViewOrNull<PosixFileAttributeView>()
            ?.setPermissions(setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE))
            ?: run {
                val file = toFile()
                file.setReadable(true)
                file.setWritable(true, true)
                file.setExecutable(true)
            }
    }

    /**
     * Copies the given [source] file to the given [target], replacing the original file if present.
     * In case of errors, it the original [target] file is restored.
     * The original file is atomically moved to another path (then deleted) so the physical file can be preserved.
     * This means that if [target] points to a currently running bash script, the script execution will not be affected.
     */
    private fun copyAndReplaceSafely(source: Path, target: Path) {
        if (target.notExists()) {
            // We copy (not move) in case of concurrent updates all wanting to move the same file
            source.copyTo(target, StandardCopyOption.REPLACE_EXISTING)
            return
        }
        // Not in a temp dir on purpose. We want to guarantee being in the same drive to allow atomic moves.
        // The name is unique to avoid issues with concurrent updates.
        val oldFileTemp = createTempFile(target.parent, "${target.name}.old")
        try {
            // Renaming a running script allows the execution to continue on unix systems (same inode on disk)
            target.moveTo(oldFileTemp, StandardCopyOption.ATOMIC_MOVE)
            try {
                // We copy (not move) in case of concurrent updates all wanting to move the same file
                source.copyTo(target, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                // We restore the old file in case of problems.
                // This is non-atomic because we don't want to fail if the file exists
                // (there could have been a partial or concurrent copy)
                oldFileTemp.moveTo(target, overwrite = true)
                throw e
            } finally {
                oldFileTemp.deleteIfExists()
            }
        } catch (e: Exception) {
            userReadableError("Couldn't update Amper script: $e")
        }
    }

    @OptIn(ProcessLeak::class)
    private fun copyAndReplaceLaterWindows(source: Path, target: Path) {
        // If some cleanup at the end of the update command takes unusually long, we don't want to risk a race and try
        // to replace amper.bat while it's still running. For this reason, we try to schedule this external process as
        // close as possible to the termination of the update command's JVM, which is why we do it in a shutdown hook.
        Runtime.getRuntime().addShutdownHook(Thread {
            startLongLivedProcess(
                // The 'ping' here is used to sleep 1 second.
                // The 'timeout' command only works in interactive consoles (thus fails in our case); 'ping' is reliable.
                // Using the IP instead of 'localhost' is a conscious choice, because localhost may involve DNS or hosts
                // file lookup, and also might be mapped to ::1 (IPv6) which might make ping fail or behave differently.
                command = listOf("cmd", "/c", "ping -n 2 127.0.0.1 & copy /y ${source.absolute().quotedForCmd()} ${target.absolute().quotedForCmd()}")
            )
        })
    }

    private fun Path.quotedForCmd(): String {
        // paths can't contain quotes on Windows
        return if (pathString.contains(' ')) "\"$pathString\"" else pathString
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
