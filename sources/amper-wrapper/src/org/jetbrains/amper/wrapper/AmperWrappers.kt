/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.wrapper

import java.nio.file.Path

private data class AmperWrapper(
    val fileName: String,
    val resourceName: String,
    val executable: Boolean,
)

@OptIn(ExperimentalStdlibApi::class)
object AmperWrappers {

    private val wrappers = listOf(
        AmperWrapper(
            fileName = "amper",
            resourceName = "wrappers/amper.template.sh",
            executable = true,
        ),
        AmperWrapper(
            fileName = "amper.bat",
            resourceName = "wrappers/amper.template.bat",
            executable = false,
        ),
    )

    /**
     * Generates Amper wrapper scripts in the specified [targetDir].
     *
     * The wrappers are generated from the template resources, substituting the values of the [amperVersion] and
     * [amperDistTgzSha256].
     *
     * @return the paths to the generated wrapper scripts
     */
    fun generate(targetDir: Path, amperVersion: String, amperDistTgzSha256: String): List<Path> =
        wrappers.map { it.generate(targetDir, amperVersion, amperDistTgzSha256) }

    private fun AmperWrapper.generate(
        targetDir: Path,
        amperVersion: String,
        amperDistTgzSha256: String
    ): Path {
        val path = targetDir.resolve(fileName)

        val wrapperText = javaClass.classLoader.getResourceAsStream(resourceName)!!
            .use { it.readAllBytes() }
            .decodeToString()

        substituteTemplatePlaceholders(
            input = wrapperText,
            outputFile = path,
            replacementRules = listOf(
                "@AMPER_VERSION@" to amperVersion,
                "@AMPER_DIST_TGZ_SHA256@" to amperDistTgzSha256,
            ),
        )

        if (executable) {
            val rc = path.toFile().setExecutable(true)
            check(rc) {
                "Unable to make file executable: $rc"
            }
        }
        return path
    }
}
