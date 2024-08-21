@file:Suppress("SameParameterValue")

import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.util.substituteTemplatePlaceholders
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.Path
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@OptIn(ExperimentalStdlibApi::class)
object AmperWrappers {
    fun generateWrappers(targetDir: Path, cliZip: Path, unixTemplate: Path, windowsTemplate: Path): List<Path> {
        val cliZipSha256 = sha256hex(cliZip)

        return buildList {
            val unixWrapper = targetDir.resolve("amper")
            add(unixWrapper)
            processTemplate(
                inputFile = unixTemplate,
                outputFile = unixWrapper,
                cliZipSha256 = cliZipSha256,
                outputWindowsLineEndings = false,
            )

            val windowsWrapper = targetDir.resolve("amper.bat")
            add(windowsWrapper)
            processTemplate(
                inputFile = Path("resources/wrappers/amper.template.bat").toAbsolutePath(),
                outputFile = windowsWrapper,
                cliZipSha256 = cliZipSha256,
                outputWindowsLineEndings = true,
            )
        }
    }

    private fun sha256hex(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(file.readBytes())
        return hash.toHexString()
    }

    private fun processTemplate(inputFile: Path, outputFile: Path, cliZipSha256: String, outputWindowsLineEndings: Boolean) {
        substituteTemplatePlaceholders(
            input = inputFile.readText(),
            outputFile = outputFile,
            placeholder = "@",
            values = listOf(
                "AMPER_VERSION" to AmperBuild.mavenVersion,
                "AMPER_DIST_SHA256" to cliZipSha256,
            ),
            outputWindowsLineEndings = outputWindowsLineEndings,
        )
    }
}
