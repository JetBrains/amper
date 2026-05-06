/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.wrapper

import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText

private data class AmperScript(
    val fileName: String,
    val templateName: String,
    val executable: Boolean = false,
)

@OptIn(ExperimentalStdlibApi::class)
object AmperWrappers {
    private val templateProvider = TemplateProvider getTemplate@ { name ->
        val stream = javaClass.classLoader.getResourceAsStream("wrappers/$name")
            ?: return@getTemplate null
        Template(
            name = name,
            text = stream.use { it.readAllBytes() }.decodeToString(),
        )
    }

    private val wrappers = listOf(
        AmperScript(
            fileName = "amper",
            templateName = "amper.template.sh",
            executable = true,
        ),
        AmperScript(
            fileName = "amper.bat",
            templateName = "amper.template.bat",
        ),
    )

    private val launchers = listOf(
        // Common launcher script that is called directly on Linux/macOS and via the busybox-w32 on Windows.
        AmperScript(
            fileName = "launcher.sh",
            templateName = "launcher.template.sh",
            executable = true,
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
        wrappers.map {
            it.generate(
                targetDir = targetDir,
                macroSubstitutions = mapOf(
                    "AMPER_VERSION" to amperVersion,
                    "AMPER_DIST_TGZ_SHA256" to amperDistTgzSha256,
                )
            )
        }

    /**
     * Generates all necessary launcher scripts in the [targetDir] directory.
     */
    fun generateLaunchers(
        targetDir: Path,
    ) {
        targetDir.createDirectory()
        launchers.forEach { it.generate(targetDir, emptyMap()) }
    }

    private fun AmperScript.generate(
        targetDir: Path,
        macroSubstitutions: Map<String, String>,
    ): Path {
        val path = targetDir.resolve(fileName)

        val template = checkNotNull(templateProvider.getTemplate(templateName)) {
            "template script not found: $templateName"
        }
        path.writeText(
            template.substitute(
                macroSubstitutions = macroSubstitutions,
                templateProvider = templateProvider,
            )
        )

        if (executable) {
            check(path.toFile().setExecutable(true)) {
                "Unable to make file $path executable"
            }
        }
        return path
    }
}
