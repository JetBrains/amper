/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import org.jetbrains.amper.cli.withBackend

internal class PublishCommand : AmperSubcommand(name = "publish") {

    private val module by option("-m", "--modules", help = "specify modules to publish, delimited by ','. " +
            "By default 'publish' command will publish all possible modules").split(",")

    private val repositoryId by argument("repository-id")

    override fun help(context: Context): String = "Publish modules to a repository"

    override suspend fun run() {
        withBackend(commonOptions, commandName) { backend ->
            backend.publish(
                modules = module?.toSet(),
                repositoryId = repositoryId,
            )
        }
    }
}
