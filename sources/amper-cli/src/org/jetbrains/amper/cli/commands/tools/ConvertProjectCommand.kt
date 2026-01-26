/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.tools

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.amper.cli.AmperVersion
import org.jetbrains.amper.cli.commands.AmperSubcommand
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.maven.MavenProjectConvertor
import org.jetbrains.amper.maven.contributor.MavenRootNotFoundException
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists

internal class ConvertProjectCommand : AmperSubcommand(name = "convert-project") {

    override fun help(context: Context): String = "Convert an existing Maven project to an Amper project."

    val pathToPomXml by option("--pom", help = "The path to the pom.xml of the project to convert.")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .default(Path(System.getProperty("user.dir")) / "pom.xml")

    val overwriteExisting: Boolean by option("--overwrite-existing", help = "Overwrite existing Amper files")
        .flag(default = false)

    override suspend fun run() {
        try {
            // mustExist doesn't apply for default value because there is no conversion from string anymore
            if (!pathToPomXml.exists()) {
                userReadableError("pom.xml file not found at: $pathToPomXml")
            }
            spanBuilder("Convert Maven Project to Amper Project").use {
                MavenProjectConvertor.convert(pathToPomXml, overwriteExisting, commonOptions.sharedCachesRoot, AmperVersion.codeIdentifier)
            }
            printSuccessfulCommandConclusion("Convert successful")
        } catch (e: MavenRootNotFoundException) {
            userReadableError(e.message ?: "Maven project root not found")
        } catch (e: FileAlreadyExistsException) {
            userReadableError("File already exists: ${e.file.absolutePath}")
        }
    }
}