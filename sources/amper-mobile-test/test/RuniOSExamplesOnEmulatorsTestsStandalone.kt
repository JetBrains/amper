/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File

class RuniOSExamplesOnEmulatorsTestsStandalone() : iOSBaseTest() {

    @Test
    fun composeiOSAppStandalone() = testRunnerPure(
        projectName = "compose-ios",
    )


    @AfterEach
    fun cleanup() {
        val projectFolder = File("${System.getProperty("user.dir")}/tempProjects")
        val appFolder = File("${System.getProperty("user.dir")}/iOSTestsAssets/app")
        projectFolder.deleteRecursively()
        appFolder.deleteRecursively()
    }

}