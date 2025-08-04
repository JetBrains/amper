/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.frontend.Model

internal class PublishCommand : AmperModelAwareCommand(name = "publish") {

    private val module by option("-m", "--modules", help = "The modules to publish, delimited by `,`. " +
            "By default, the `publish` command will publish all possible modules").split(",")

    private val repositoryId by argument("repository-id")

    override fun help(context: Context): String = "Publish modules to a repository"

    override suspend fun run(cliContext: CliContext, model: Model) {
        withBackend(cliContext, model) { backend ->
            backend.publish(
                modules = module?.toSet(),
                repositoryId = repositoryId,
            )
        }
        printSuccessfulCommandConclusion("Publication successful")
    }
}
