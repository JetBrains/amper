/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.jetbrains.amper.core.AmperBuild
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

object LocalAmperCli {
    private val rootPublicationDir = TestUtil.m2repository.resolve("org/jetbrains/amper/cli/${AmperBuild.mavenVersion}")

    val distZip: Path = rootPublicationDir.resolve("cli-${AmperBuild.mavenVersion}-dist.zip")
    val wrapperBat: Path = rootPublicationDir.resolve("cli-${AmperBuild.mavenVersion}-wrapper.bat")
    val wrapperSh: Path = rootPublicationDir.resolve("cli-${AmperBuild.mavenVersion}-wrapper")

    fun checkPublicationIntegrity() {
        check(distZip.exists()) { "Amper distribution is missing in maven local: $distZip not found" }
        check(wrapperBat.exists()) { "Amper wrapper.bat is missing in maven local: $wrapperBat not found" }
        check(wrapperSh.exists()) { "Amper wrapper (sh) is missing in maven local: $wrapperSh not found" }

        assertTrue(wrapperBat.readText().count { it == '\r' } > 10,
            "Windows wrapper must have \\r in line separators: $wrapperBat")

        assertTrue(wrapperSh.readText().count { it == '\r' } == 0,
            "Unix wrapper must not have \\r in line separators: $wrapperSh")
    }
}
