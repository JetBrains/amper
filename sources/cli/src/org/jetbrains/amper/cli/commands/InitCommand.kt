/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.generator.ProjectGenerator
import kotlin.io.path.Path

internal class InitCommand : AmperSubcommand(name = "init") {

    private val template by argument(help = "project template name substring, e.g., 'jvm-cli'").optional()

    override fun help(context: Context): String = "Initialize a new Amper project based on a template"

    override suspend fun run() {
        val directory = commonOptions.explicitRoot ?: Path(System.getProperty("user.dir"))
        ProjectGenerator(terminal = Terminal()).initProject(template, directory)
    }
}
