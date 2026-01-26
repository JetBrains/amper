/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jvm

import org.jetbrains.amper.cli.CliProblemReporter
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.frontend.schema.DefaultVersions
import org.jetbrains.amper.frontend.schema.DiscouragedDirectDefaultVersionAccess
import org.jetbrains.amper.frontend.schema.JdkSelectionMode
import org.jetbrains.amper.frontend.schema.JdkSettings
import org.jetbrains.amper.jdk.provisioning.Jdk
import org.jetbrains.amper.jdk.provisioning.JdkProvider
import org.jetbrains.amper.jdk.provisioning.JdkProvisioningCriteria
import org.jetbrains.amper.jdk.provisioning.orElse

/**
 * Finds or provisions a JDK matching the given [jdkSettings], or fails with a [userReadableError].
 *
 * Potential global errors about `JAVA_HOME` are logged once per instance of [JdkProvider].
 */
internal suspend fun JdkProvider.getJdkOrUserError(jdkSettings: JdkSettings): Jdk =
    context(CliProblemReporter) {
        getJdk(jdkSettings).orElse { errorMessage ->
            userReadableError(errorMessage)
        }
    }

/**
 * Finds or provisions a JDK matching the default settings, or fails with a [userReadableError].
 *
 * This JDK is what users get when a module uses the default JDK settings.
 *
 * Potential global errors about `JAVA_HOME` are logged once per instance of [JdkProvider].
 */
@OptIn(DiscouragedDirectDefaultVersionAccess::class) // this is the point of this function
suspend fun JdkProvider.getDefaultJdk(selectionMode: JdkSelectionMode = JdkSelectionMode.auto): Jdk =
    context(CliProblemReporter) {
        getJdk(
            criteria = JdkProvisioningCriteria(majorVersion = DefaultVersions.jdk),
            selectionMode = selectionMode,
        ).orElse { errorMessage ->
            userReadableError("Could not provide the default JDK: $errorMessage")
        }
    }
