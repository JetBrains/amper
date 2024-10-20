@file:Suppress("SameParameterValue")

import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.util.substituteTemplatePlaceholders
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@OptIn(ExperimentalStdlibApi::class)
object AmperWrappers {
    fun generateWrappers(
        targetDir: Path,
        cliDistTgz: Path,
        unixTemplate: Path,
        windowsTemplate: Path,
    ): List<Path> {
        val cliDistSha256 = sha256hex(cliDistTgz)

        return buildList {
            val unixWrapper = targetDir.resolve("amper")
            add(unixWrapper)
            processTemplate(
                inputFile = unixTemplate,
                outputFile = unixWrapper,
                cliTgzSha256 = cliDistSha256,
                outputWindowsLineEndings = false,
            )

            val windowsWrapper = targetDir.resolve("amper.bat")
            add(windowsWrapper)
            processTemplate(
                inputFile = windowsTemplate,
                outputFile = windowsWrapper,
                cliTgzSha256 = cliDistSha256,
                outputWindowsLineEndings = true,
            )
        }
    }

    private fun sha256hex(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(file.readBytes())
        return hash.toHexString()
    }

    private fun processTemplate(
        inputFile: Path,
        outputFile: Path,
        cliTgzSha256: String,
        outputWindowsLineEndings: Boolean,
    ) {
        substituteTemplatePlaceholders(
            input = inputFile.readText(),
            outputFile = outputFile,
            placeholder = "@",
            values = listOf(
                "AMPER_VERSION" to AmperBuild.mavenVersion,
                "AMPER_DIST_TGZ_SHA256" to cliTgzSha256,
            ),
            outputWindowsLineEndings = outputWindowsLineEndings,
        )
    }
}
