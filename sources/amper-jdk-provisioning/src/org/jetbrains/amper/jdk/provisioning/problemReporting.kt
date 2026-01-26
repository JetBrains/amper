/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


package org.jetbrains.amper.jdk.provisioning

import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemSource
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.GlobalBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MessageBundle
import org.jetbrains.amper.problems.reporting.NonIdealDiagnostic
import org.jetbrains.annotations.Nls

object ProvisioningBundle : MessageBundle("messages.JdkProvisioningBundle")

abstract class ProvisioningProblem(
    override val source: BuildProblemSource,
    val messageKey: String,
    vararg val params: Any?,
) : BuildProblem {

    override val buildProblemId: BuildProblemId
        get() = messageKey

    override val message: @Nls String
        get() = ProvisioningBundle.message(messageKey, params)

    override val level: Level
        get() = Level.Warning

    override val type: BuildProblemType
        get() = BuildProblemType.Generic
}

@OptIn(NonIdealDiagnostic::class) // JAVA_HOME is a global thing, so we have no choice here
class InvalidJavaHome(
    val javaHomeValue: String,
    errorKey: String,
    vararg params: Any?,
) : ProvisioningProblem(
    source = GlobalBuildProblemSource,
    messageKey = errorKey,
    params = params,
)
