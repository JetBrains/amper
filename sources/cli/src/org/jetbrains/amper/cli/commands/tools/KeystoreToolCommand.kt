/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.tools

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.amper.cli.commands.RootCommand
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.keystore.KeystoreGenerationException
import org.jetbrains.amper.keystore.KeystoreGenerator
import kotlin.io.path.Path
import kotlin.io.path.div

class KeystoreToolCommand : SuspendingCliktCommand(name = "generate-keystore") {

    private val commonOptions by requireObject<RootCommand.CommonOptions>()

    private val propertiesFile by option(
        "--properties-file",
        help = "Path to properties file which is used to populate storeFile, storePassword, keyAlias, keyPassword during the generation"
    ).path(canBeDir = false, mustExist = true, mustBeReadable = true)

    private val storeFile by option("--keystore-file", help = "Where to store keystore")
        .path(canBeDir = false, mustExist = false)
        .default((Path(System.getProperty("user.home")) / ".keystores" / "release.keystore"))

    private val storePassword by option("--keystore-password", help = "Keystore password").default("")

    private val keyAlias by option("--key-alias", help = "Key alias").default("android")

    private val keyPassword by option("--key-password", help = "Key password").default("")

    private val dn by option("--dn", help = "issuer").default("CN=${System.getProperty("user.name", "Unknown")}")

    override fun help(context: Context): String = "Generate keystore"

    override suspend fun run() {
        withBackend(commonOptions, commandName, setupEnvironment = true) {
            try {
                propertiesFile?.let {
                    KeystoreGenerator.createNewKeystore(it, dn)
                } ?: run {
                    KeystoreGenerator.createNewKeystore(storeFile, storePassword, keyAlias, keyPassword, dn)
                }
            } catch (e: KeystoreGenerationException) {
                userReadableError(e.message)
            }
        }
    }
}
